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

import com.iscas.satellite.utils.Satellite;

public class Tle2Czml {

    private static final double BILLBOARD_SCALE = 1.5;
    private static final String LABEL_FONT = "11pt Lucida Console";
    private static final String SATELLITE_IMAGE_URI = "<base64 png>";

    private static final int MULTIPLIER = 60;
    private static final String DESCRIPTION_TEMPLATE = "Orbit of Satellite: ";
    private static final int TIME_STEP = 300;

    private static final int[] DEFAULT_RGBA = { 213, 255, 0, 255 };

    /* ---------------- Satellite ---------------- */

    // public static class Satellite {
    // public String[] rawTle;
    // public TLE tleObject;
    // public int[] rgba;
    // public String satName;
    // public double orbitalMinutes;

    // public Satellite(String[] rawTle, TLE tleObject, int[] rgba) {
    // double revsPerDay = Double.parseDouble(rawTle[2].substring(52, 63).trim());

    // this.rawTle = rawTle;
    // this.tleObject = tleObject;
    // this.rgba = rgba;
    // this.satName = rawTle[0].trim();
    // this.orbitalMinutes = (24.0 / revsPerDay) * 60.0;
    // }
    // }

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

            list.add(new Satellite(raw, "/statics/orekit-data", col.nextColor()));
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

        CZML czmlDoc = new CZML();
        CZMLPacket docPacket = new CZMLPacket("document");
        docPacket.version = "1.0";
        czmlDoc.addPacket(docPacket);

        for (Satellite sat : sats) {
            CZMLPacket satPacket = new CZMLPacket(sat.satName);
            satPacket.position = createPosition(start, end, sat.tleObject);
            satPacket.availability = interval(start, end);
            satPacket.description = new Description(sat.satName);
            satPacket.path = createPath(satPacket.availability, sat, start, end);
            czmlDoc.addPacket(satPacket);
        }

        return czmlDoc.toJsonString();
    }

    public static void main(String[] args) throws Exception {
        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
        File folder = new ClassPathResource("/statics/orekit-data").getFile();
        manager.addProvider(new DirectoryCrawler(folder));
        ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime end = start.plusHours(48);
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("classpath:/statics/tles/GF-7.tle");

        try (InputStream is = resource.getInputStream()) {
            String tleString = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String czmlDocString = tlesToCzml(tleString, start, end);
            try {
                Files.writeString(java.nio.file.Path.of("out.json"), czmlDocString);
            } catch (Exception e) {
                System.out.println(e);
            }
        }

    }
}
