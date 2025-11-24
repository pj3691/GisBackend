package com.iscas.satellite.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

/**
 * 简化版 CZML 核心类及优先类：CZML, CZMLPacket, Billboard, Description,
 * Label, Path, Position。依赖 Jackson (com.fasterxml.jackson.databind)。
 */
public class CzmlGenerator {
    private static final ObjectMapper M = new ObjectMapper();
    private static final int TIME_STEP = 300;

    // ---------------- CZML 文档 ----------------
    public static class CZML {
        private final List<CZMLPacket> packets = new ArrayList<>();

        public void addPacket(CZMLPacket p) {
            packets.add(p);
        }

        public ArrayNode toJsonArray() {
            ArrayNode arr = M.createArrayNode();
            for (CZMLPacket p : packets)
                arr.add(p.toJson());
            return arr;
        }

        public String toJsonString() {
            try {
                return M.writerWithDefaultPrettyPrinter().writeValueAsString(toJsonArray());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ---------------- CZMLPacket ----------------
    public static class CZMLPacket {
        public String id;
        public String availability;
        public String version;
        public Description description;
        public Billboard billboard;
        public Label label;
        public Path path;
        public Position position;
        public Object clock; // 可扩展为更复杂类型

        public CZMLPacket() {
        }

        public CZMLPacket(String id) {
            this.id = id;
        }

        public ObjectNode toJson() {
            ObjectNode n = M.createObjectNode();
            if (id != null)
                n.put("id", id);
            if (version != null)
                n.put("version", version);
            if (availability != null)
                n.put("availability", availability);
            if (clock != null)
                n.set("clock", M.valueToTree(clock));
            if (description != null)
                n.set("description", description.toJson());
            if (billboard != null)
                n.set("billboard", billboard.toJson());
            if (label != null)
                n.set("label", label.toJson());
            if (path != null)
                n.set("path", path.toJson());
            if (position != null)
                n.set("position", position.toJson());
            return n;
        }
    }

    // ---------------- Description ----------------
    public static class Description {
        public String text;

        public Description(String text) {
            this.text = text;
        }

        public ObjectNode toJson() {
            ObjectNode n = M.createObjectNode();
            n.put("string", text == null ? "" : text);
            return n;
        }
    }

    // ---------------- Billboard ----------------
    public static class Billboard {
        public String image;
        public Double scale;
        public Boolean show;

        public Billboard() {
        }

        public ObjectNode toJson() {
            ObjectNode n = M.createObjectNode();
            if (image != null)
                n.put("image", image);
            if (scale != null)
                n.put("scale", scale);
            if (show != null)
                n.put("show", show);
            return n;
        }
    }

    // ---------------- Label ----------------
    public static class Label {
        public String text;
        public Boolean show;
        public Integer outlineWidth;
        public Map<String, Object> fillColor; // eg {"rgba":[r,g,b,a]}
        public String font;
        public String horizontalOrigin;
        public String verticalOrigin;
        public Object pixelOffset; // 支持 cartesian2 数组等

        public Label() {
        }

        public Label(String text) {
            this.text = text;
            this.show = true;
        }

        public ObjectNode toJson() {
            ObjectNode n = M.createObjectNode();
            if (show != null)
                n.put("show", show);
            if (text != null)
                n.put("text", text);
            if (outlineWidth != null)
                n.put("outlineWidth", outlineWidth);
            if (fillColor != null)
                n.set("fillColor", M.valueToTree(fillColor));
            if (font != null)
                n.put("font", font);
            if (horizontalOrigin != null)
                n.put("horizontalOrigin", horizontalOrigin);
            if (verticalOrigin != null)
                n.put("verticalOrigin", verticalOrigin);
            if (pixelOffset != null)
                n.set("pixelOffset", M.valueToTree(pixelOffset));
            return n;
        }
    }

    // ---------------- Path ----------------
    public static class Path {
        public List<Object> show; // 支持 time-interval boolean pairs
        public Integer width;
        public Map<String, Object> material; // 支持 {"solidColor":{"color":{"rgba":[...]}}}
        public Integer resolution;
        public List<Map<String, Object>> leadTime; // list of lead/trail segments
        public List<Map<String, Object>> trailTime;

        public Path() {
        }

        public ObjectNode toJson() {
            ObjectNode n = M.createObjectNode();
            if (show != null)
                n.set("show", M.valueToTree(show));
            if (width != null)
                n.put("width", width);
            if (material != null)
                n.set("material", M.valueToTree(material));
            if (resolution != null)
                n.put("resolution", resolution);
            if (leadTime != null)
                n.set("leadTime", M.valueToTree(leadTime));
            if (trailTime != null)
                n.set("trailTime", M.valueToTree(trailTime));
            return n;
        }
    }

    // ---------------- Position ----------------
    public static class Position {
        public String epoch;
        public String referenceFrame;
        public String interpolationAlgorithm;
        public Integer interpolationDegree;
        // 支持 cartesian 常值或时间序列（混合 ISO 时间字符串与数值）
        public List<Double> cartesian; // e.g. [t0offset, x,y,z, t1offset, x,y,z, ...] or simple [x,y,z]

        public Position() {
        }

        public ObjectNode toJson() {
            ObjectNode n = M.createObjectNode();
            if (epoch != null)
                n.put("epoch", epoch);
            if (referenceFrame != null)
                n.put("referenceFrame", referenceFrame);
            if (interpolationAlgorithm != null)
                n.put("interpolationAlgorithm", interpolationAlgorithm);
            if (interpolationDegree != null)
                n.put("interpolationDegree", interpolationDegree);
            if (cartesian != null)
                n.set("cartesian", M.valueToTree(cartesian));
            return n;
        }
    }

    private static String interval(ZonedDateTime start, ZonedDateTime end) {
        return start.toString() + "/" + end.toString();
    }

    private static Path createPath(String totalInterval, Satellite sat, ZonedDateTime start, ZonedDateTime end,
            int[] rgba) {
        Path path = new Path();
        path.show = List.of(
                Map.of("interval", totalInterval, "boolean", true));
        path.width = 1;
        path.material = Map.of("solidColor",
                Map.of("color", Map.of("rgba", rgba)));
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

    private static AbsoluteDate toAbsoluteDate(ZonedDateTime zdt) {
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

    private static int[] getRandomColor() {
        return new int[] { (int) (Math.random() * 255), (int) (Math.random() * 255), (int) (Math.random() * 255), 255 };

    }

    private static Position createPosition(ZonedDateTime start, ZonedDateTime end, TLE tle) {
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

    public static String getSatelliteCzml(Satellite satellite, ZonedDateTime start, ZonedDateTime end,
            Optional<int[]> rgbaOptional)
            throws Exception {
        if (start == null)
            start = ZonedDateTime.now(ZoneOffset.UTC);
        if (end == null)
            end = start.plusHours(24);

        CZML czmlDoc = new CZML();
        CZMLPacket docPacket = new CZMLPacket("document");
        docPacket.version = "1.0";
        czmlDoc.addPacket(docPacket);

        CZMLPacket satPacket = new CZMLPacket(satellite.satName);
        satPacket.position = createPosition(start, end, satellite.tleObject);
        satPacket.availability = interval(start, end);
        satPacket.description = new Description(satellite.satName);
        int[] rgba = rgbaOptional.orElse(getRandomColor());
        satPacket.path = createPath(satPacket.availability, satellite, start, end, rgba);
        czmlDoc.addPacket(satPacket);

        return czmlDoc.toJsonString();
    }

    // ---------------- 简单示例 main ----------------
    public static void main(String[] args) {
        CZML doc = new CZML();
        CZMLPacket p = new CZMLPacket("document");
        p.version = "1.0";
        doc.addPacket(p);

        CZMLPacket sat = new CZMLPacket("sat-1");
        sat.availability = "2025-01-01T00:00:00Z/2025-01-02T00:00:00Z";
        sat.description = new Description("示例卫星");
        Billboard bb = new Billboard();
        // bb.image = "https://example.com/i.png";
        bb.scale = 1.0;
        sat.billboard = bb;
        Label lab = new Label("sat-1");
        lab.font = "11pt";
        sat.label = lab;
        Position pos = new Position();
        pos.epoch = "2025-01-01T00:00:00Z";
        // pos.cartesian = Arrays.asList(0, 1000.0, 2000.0, 3000.0);
        sat.position = pos;
        doc.addPacket(sat);
        try {
            Files.writeString(java.nio.file.Path.of("out.json"), doc.toJsonString());
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
