package com.iscas.satellite.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.propagation.analytical.tle.SGP4;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.iscas.satellite.utils.CzmlGenerator.*;
import com.iscas.satellite.utils.CzmlGenerator.Path;

public class Tle2Czml2 {

    private static final double BILLBOARD_SCALE = 1.5;
    private static final String LABEL_FONT = "11pt Lucida Console";
    private static final String SATELLITE_IMAGE_URI = "<base64 png>";

    private static final int MULTIPLIER = 60;
    private static final String DESCRIPTION_TEMPLATE = "Orbit of Satellite: ";
    private static final int TIME_STEP = 300;

    private static final int[] DEFAULT_RGBA = { 213, 255, 0, 255 };

    /* ---------------- Satellite ---------------- */

    public static class Satellite {
        public String[] rawTle;
        // public SGP4SatData tleObject;
        public int[] rgba;
        public String satName;
        public double orbitalMinutes;
        public ZonedDateTime tleEpoch;

        public Satellite(String[] rawTle, String tleObject, int[] rgba) {
            this.rawTle = rawTle;
            // this.tleObject = tleObject;
            this.rgba = rgba;
            this.satName = rawTle[0].trim();

            double revsPerDay = Double.parseDouble(rawTle[2].substring(52, 63).trim());
            this.orbitalMinutes = (24.0 / revsPerDay) * 60.0;

            this.tleEpoch = parseTleEpoch(tleObject);
        }
    }

    /* ---------------- Colors ---------------- */

    public static class Colors {
        private List<int[]> rgbs = new ArrayList<>();
        private int index = 0;

        public Colors() throws IOException {
            List<String> lines = Files.readAllLines(Paths.get("rgba_list.txt"));
            for (String line : lines) {
                String[] cols = line.split("\\s+");
                int[] rgba = new int[] {
                        Integer.parseInt(cols[0]),
                        Integer.parseInt(cols[1]),
                        Integer.parseInt(cols[2]),
                        255
                };
                rgbs.add(rgba);
            }
        }

        public int[] nextColor() {
            int[] c = rgbs.get(index);
            index = (index + 1) % rgbs.size();
            return c;
        }
    }

    /* ---------------- Utilities ---------------- */

    private static String interval(ZonedDateTime start, ZonedDateTime end) {
        return start.toString() + "/" + end.toString();
    }

    private static ZonedDateTime parseTleEpoch(String tle) {
        // 自行根据你的 TLE 解析方式
        return ZonedDateTime.now();
    }

    /* ---------------- CZML Construction ---------------- */

    public static CZML createCzmlFile(ZonedDateTime start, ZonedDateTime end) {
        CZML doc = new CZML();
        CZMLPacket packet = new CZMLPacket("document");
        // packet.putClock("interval", interval(start, end));
        // packet.putClock("currentTime", start.toString());
        // packet.putClock("multiplier", MULTIPLIER);
        // packet.putClock("range", "LOOP_STOP");
        // packet.putClock("step", "SYSTEM_CLOCK_MULTIPLIER");
        // doc.add(packet);
        return doc;
    }

    private static Path createPath(String totalInterval, Satellite sat,
            ZonedDateTime start, ZonedDateTime end) {

        Path path = new Path();
        path.show = List.of(
                Map.of("interval", totalInterval, "boolean", true));
        path.width = 1;
        path.material = Map.of("solidColor",
                Map.of("color", Map.of("rgba", sat.rgba)));
        path.resolution = 120;

        // 轨迹 lead/trail 计算过程保持原样
        List<Map<String, Object>> leadTimes = new ArrayList<>();
        List<Map<String, Object>> trailTimes = new ArrayList<>();

        long minutes = Duration.between(start, end).toMinutes();
        long fullOrbits = (long) (minutes / sat.orbitalMinutes);
        long left = (long) (minutes % sat.orbitalMinutes);

        ZonedDateTime t0 = start;
        ZonedDateTime t1 = t0.plusMinutes(left);

        double orbitalSec = sat.orbitalMinutes * 60.0;

        for (int i = 0; i < fullOrbits + 1; i++) {

            leadTimes.add(Map.of(
                    "interval", t0 + "/" + t1,
                    "epoch", t0.toString(),
                    "number", List.of(
                            0, orbitalSec,
                            orbitalSec, 0)));

            trailTimes.add(Map.of(
                    "interval", t0 + "/" + t1,
                    "epoch", t0.toString(),
                    "number", List.of(
                            0, 0,
                            orbitalSec, orbitalSec)));

            t0 = t1;
            t1 = t0.plusMinutes((long) sat.orbitalMinutes);
        }

        path.leadTime = leadTimes;
        path.trailTime = trailTimes;

        return path;
    }

    public static AbsoluteDate toAbsoluteDate(ZonedDateTime zdt) {
        // 转 UTC（Orekit 强制用 UTC 或者你手动指定 TimeScale）
        ZonedDateTime utc = zdt.withZoneSameInstant(ZoneOffset.UTC);

        // 日期与时间拆分
        DateComponents date = new DateComponents(
                utc.getYear(),
                utc.getMonthValue(),
                utc.getDayOfMonth());

        TimeComponents time = new TimeComponents(
                utc.getHour(),
                utc.getMinute(),
                utc.getSecond() + utc.getNano() / 1e9);

        // 使用 UTC 时标
        TimeScale utcScale = TimeScalesFactory.getUTC();

        return new AbsoluteDate(date, time, utcScale);
    }

    private static Position createPosition(ZonedDateTime start, ZonedDateTime end,
            TLE tle) {

        Position pos = new Position();
        pos.interpolationAlgorithm = "LAGRANGE";
        pos.interpolationDegree = 5;
        pos.referenceFrame = "INERTIAL";
        pos.epoch = start.toString();

        long seconds = Duration.between(start, end).getSeconds();
        int count = (int) (seconds / 300) + 5;

        List<Double> values = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ZonedDateTime t = start.plusSeconds(i * TIME_STEP);

            TLEPropagator p = TLEPropagator.selectExtrapolator(tle);
            Vector3D posVector3 = p.getPVCoordinates(toAbsoluteDate(t)).getPosition();

            values.add((double) (i * TIME_STEP)); // seconds
            values.add(posVector3.getX());
            values.add(posVector3.getY());
            values.add(posVector3.getZ());
        }

        pos.cartesian = values;
        return pos;
    }

    /* ---------------- Read TLE, Build CZML ---------------- */

    public static List<Satellite> readTles(String tles, Colors col) {
        List<Satellite> list = new ArrayList<>();
        String[] lines = tles.split("\n");

        for (int i = 0; i < lines.length; i += 3) {
            String[] raw = new String[] { lines[i], lines[i + 1], lines[i + 2] };
            TLE tle = new TLE(raw[1], raw[2]);
            String satData = "";
            // satData = TLE.parse(raw[1], raw[2]);

            list.add(new Satellite(raw, satData, col.nextColor()));
        }

        return list;
    }

    public static String tlesToCzml(String tles, ZonedDateTime start, ZonedDateTime end) throws Exception {
        Colors colors = new Colors();
        List<Satellite> sats = readTles(tles, colors);

        if (start == null)
            start = ZonedDateTime.now(ZoneOffset.UTC);
        if (end == null)
            end = start.plusHours(24);

        CZML doc = createCzmlFile(start, end);

        // for (Satellite sat : sats) {
        // CZMLPacket p = createSatellitePacket(sat, start, end);
        // doc.add(p);
        // }

        return "";
    }

    public static void createCzml(String inputPath, String outputPath,
            ZonedDateTime start, ZonedDateTime end) throws Exception {

        String tles = Files.readString(Paths.get(inputPath));
        String czml = tlesToCzml(tles, start, end);

        if (outputPath == null)
            outputPath = "orbit.czml";

        Files.writeString(Paths.get(outputPath), czml);
    }

    public static void main(String[] args) throws Exception {
        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
        File folder = new ClassPathResource("/statics/orekit-data").getFile();
        manager.addProvider(new DirectoryCrawler(folder));
        ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime end = start.plusHours(24);
        Colors colors = new Colors();
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("classpath:/statics/tles/GF-7.tle");

        try (InputStream is = resource.getInputStream()) {
            String te1 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = te1.split("\r?\n");
            String l1 = lines[1];
            String l2 = lines[2];
            TLE tle = new TLE(l1, l2);

            CZML doc = new CZML();
            CZMLPacket docPacket = new CZMLPacket("document");
            docPacket.version = "1.0";
            doc.addPacket(docPacket);
            CZMLPacket satPacket = new CZMLPacket("sat-1");
            doc.addPacket(satPacket);
            satPacket.position = createPosition(start, end, tle);
            satPacket.availability = interval(start, end);
            satPacket.description = new Description("示例卫星");
            satPacket.path = createPath(satPacket.availability, readTles(te1, colors).get(0), start, end);
            try {
                Files.writeString(java.nio.file.Path.of("out.json"), doc.toJsonString().toString());
            } catch (Exception e) {
                System.out.println(e);
            }

            // TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
            // Position position = createPosition(start, end, tle);
            // try {
            // Files.writeString(java.nio.file.Path.of("out.json"),
            // position.toJson().toString());
            // } catch (Exception e) {
            // System.out.println(e);
            // }
            // // 获取ITRF地固系
            // Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            // AbsoluteDate targetDate = new AbsoluteDate();
            // // 传播到指定时间，获取在ITRF系中的位置
            // PVCoordinates pv = propagator.getPVCoordinates(targetDate, itrf);
            // Vector3D position = pv.getPosition();
            // // 创建WGS84椭球体
            // OneAxisEllipsoid wgs84 = new OneAxisEllipsoid(
            // Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
            // Constants.WGS84_EARTH_FLATTENING,
            // itrf);
            // // 将直角坐标转换为大地坐标
            // GeodeticPoint geoPoint = wgs84.transform(position, itrf, targetDate);
            // double lon = Math.toDegrees(geoPoint.getLongitude()); // 经度(度)
            // double lat = Math.toDegrees(geoPoint.getLatitude()); // 纬度(度)
            // double h = geoPoint.getAltitude(); // 高度(米)
            // System.out.println(lon);
            // String availability = interval(start, end);
            // List<Satellite> sats = readTles(te1, colors);
            // Satellite sat = sats.get(0);
            // Path path123Path = createPath(availability, sats.get(0), start, end);
            // try {
            // Files.writeString(java.nio.file.Path.of("out.json"),
            // path123Path.toJson().toString());
            // } catch (Exception e) {
            // System.out.println(e);
            // }
        }

    }
}
