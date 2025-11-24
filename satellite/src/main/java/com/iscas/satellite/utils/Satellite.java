package com.iscas.satellite.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.events.ElevationExtremumDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.EventsLogger;
import org.orekit.propagation.events.EventsLogger.LoggedEvent;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.Constants;

/**
 * 卫星类
 */
public class Satellite {
    public String[] rawTle;
    public TLE tleObject;
    public TLEPropagator propagator;
    public String satName;
    public double orbitalMinutes;

    /**
     * 构造一个卫星
     * 
     * @param tleData        tle文件内容（三行）
     * @param orekitDataPath orekit-data文件路径 eg. /statics/orekit-data
     */
    public Satellite(String[] rawTle, String orekitDataPath) {
        try {
            DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
            if (orekitDataPath == null)
                throw new IllegalArgumentException("name is null");
            File folder = new ClassPathResource(orekitDataPath).getFile();
            manager.addProvider(new DirectoryCrawler(folder));
            if (rawTle.length < 3) {
                String[] result = new String[rawTle.length + 1];
                result[0] = "satellite";
                System.arraycopy(rawTle, 0, result, 1, rawTle.length);
            }
            String l1 = rawTle[1];
            String l2 = rawTle[2];
            TLE tleObject = new TLE(l1, l2);
            this.propagator = TLEPropagator.selectExtrapolator(tleObject);
            double revsPerDay = Double.parseDouble(rawTle[2].substring(52, 63).trim());

            this.rawTle = rawTle;
            this.tleObject = tleObject;
            this.satName = rawTle[0].trim();
            this.orbitalMinutes = (24.0 / revsPerDay) * 60.0;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load orekit-data from classpath", e);
        }
    }

    public AbsoluteDate strToAbsoluteDate(String dateStr) {
        // 拆分日期和时间
        String[] parts = dateStr.split(" ");
        String[] dateParts = parts[0].split("-");
        String[] timeParts = parts[1].split(":");

        // 年月日
        int year = Integer.parseInt(dateParts[0]);
        int month = Integer.parseInt(dateParts[1]);
        int day = Integer.parseInt(dateParts[2]);

        // 时分秒
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);
        int second = Integer.parseInt(timeParts[2]);

        // 使用 UTC 时间尺度
        TimeScale utc = TimeScalesFactory.getUTC();

        // 创建 AbsoluteDate
        return new AbsoluteDate(
                new DateComponents(year, month, day),
                new TimeComponents(hour, minute, second),
                utc);
    }

    /**
     * 判断卫星在某个时刻是否经过某个位置
     * 
     * @param targetDateStr    计算过境的时间字符串 eg 2025-10-11 15:24:41
     * @param observerPosition 计算过境的位置 eg new Double[] { 120.0, 40.0, 0.0 }
     * @param toTopAngle       过境的天顶角（当计算出的天顶角小于这个角度时则判定为过境）
     * @return 是否过境
     */
    public Boolean isToTop(String targetDateStr, Double[] observerPosition, Double toTopAngle) {
        AbsoluteDate targetDate = this.strToAbsoluteDate(targetDateStr);
        SpacecraftState state = propagator.propagate(targetDate);

        // 5) 观测点经纬高（radians, radians, meters）
        double lonDeg = observerPosition[0]; // 经度
        double latDeg = observerPosition[1]; // 纬度
        double altM = observerPosition[2]; // 高度（m）

        GeodeticPoint obsPoint = new GeodeticPoint(Math.toRadians(latDeg),
                Math.toRadians(lonDeg),
                altM);

        // 6) 地球椭球体（ITRF，WGS84）
        OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING,
                FramesFactory.getITRF(IERSConventions.IERS_2010, true));

        // 7) 构造 TopocentricFrame（局部天顶框，Z 指向本地天顶）
        TopocentricFrame topo = new TopocentricFrame(earth, obsPoint, "observer");

        // 8) 得到卫星在 state.frame 中的位置向量（Vector3D）
        PVCoordinates pvInStateFrame = state.getPVCoordinates();
        Vector3D satPos = pvInStateFrame.getPosition(); // 在 state.getFrame() 所表达的坐标系中

        // 9) 计算仰角（rad），方法：topo.getElevation(extPoint, frame, date)
        double elevationRad = topo.getElevation(satPos, state.getFrame(), targetDate);

        // 天顶角 = 90° - elevation

        double zenithRad = Math.toDegrees(Math.PI / 2.0 - elevationRad);

        return zenithRad < toTopAngle;
    }

    /**
     * 获取某个时间段卫星的路径czml
     * 
     * @param start        开始时间
     * @param end          结束时间
     * @param rgbaOptional 轨迹颜色，可选参数
     * @return czml字符串（可用于导出为.czml 或者 .json 文件）
     * @throws Exception
     */
    public String getCzmlDocString(ZonedDateTime start, ZonedDateTime end, Optional<int[]> rgbaOptional)
            throws Exception {
        if (start == null)
            start = ZonedDateTime.now(ZoneOffset.UTC);
        if (end == null)
            end = start.plusHours(24);
        String czmlDocString = CzmlGenerator.getSatelliteCzml(this, start, end, rgbaOptional);

        return czmlDocString;
    }

    public class toTopEvent {
        public double elevate;
        public String timeString;
        public String eventNameString;

        toTopEvent(double elevate, String timeString, String eventNameString) {
            this.elevate = elevate;
            this.timeString = timeString;
            this.eventNameString = eventNameString;
        }

    }

    public String toTopEventDetect(AbsoluteDate start, AbsoluteDate end, Double[] observerPosition,
            Double toTopAngle) {
        // 初始化地球模型和地面站
        OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING,
                FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        GeodeticPoint stationGeo = new GeodeticPoint(
                Math.toRadians(observerPosition[0]), // 纬度
                Math.toRadians(observerPosition[1]), // 经度
                observerPosition[2] // 地面站高度
        );

        TopocentricFrame stationFrame = new TopocentricFrame(earth, stationGeo, "Station");

        // 创建 ElevationDetector：最低仰角
        double minElevation = Math.toRadians(toTopAngle);
        ElevationDetector elevationDetector = new ElevationDetector(stationFrame)
                .withConstantElevation(minElevation)
                .withHandler(new ContinueOnEvent());

        // 检测仰角极值 (最高点)
        ElevationExtremumDetector maxElevDetector = new ElevationExtremumDetector(stationFrame)
                .withThreshold(1e-6)
                .withHandler(new ContinueOnEvent());

        // 添加检测器
        this.propagator.addEventDetector(elevationDetector);
        this.propagator.addEventDetector(maxElevDetector);

        // 用 EventsLogger 记录事件
        EventsLogger logger = new EventsLogger();
        logger.monitorDetector(elevationDetector);
        logger.monitorDetector(maxElevDetector);

        propagator.propagate(start, end);

        List<toTopEvent> toTopEventsList = new ArrayList<toTopEvent>();
        System.out.println(logger.getLoggedEvents());
        // 读取检测到的事件
        for (LoggedEvent evt : logger.getLoggedEvents()) {

            EventDetector detector = evt.getEventDetector();
            AbsoluteDate date = evt.getState().getDate();
            PVCoordinates pv = evt.getState().getPVCoordinates(evt.getState().getFrame());
            TopocentricFrame topo = ((ElevationDetector) detector).getTopocentricFrame();
            Vector3D satTopo = topo.getTransformTo(evt.getState().getFrame(), date)
                    .transformPosition(pv.getPosition());
            double elevation = Math.asin(satTopo.getZ() / satTopo.getNorm());
            double elevationDeg = Math.toDegrees(elevation);

            // --- AOS：卫星上升穿越地平线 ---
            if (detector instanceof ElevationDetector && evt.isIncreasing()) {
                toTopEventsList.add(new toTopEvent(elevationDeg, evt.getState().getDate().toString(), "UP"));
            }

            // --- LOS：卫星下降穿越地平线 ---
            if (detector instanceof ElevationDetector && !evt.isIncreasing()) {
                toTopEventsList.add(new toTopEvent(elevationDeg, evt.getState().getDate().toString(), "DOWN"));

            }

            // --- MAX：仰角达到最大值 ---
            if (detector instanceof ElevationExtremumDetector) {
                toTopEventsList.add(new toTopEvent(elevationDeg, evt.getState().getDate().toString(), "MAX"));
            }

        }

        return toTopEventsList.toString();

    }

    public static void main(String[] args) {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("classpath:/statics/tles/GF-7.tle");
        try (InputStream is = resource.getInputStream()) {
            // 构造一个卫星
            String te1 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = te1.split("\n");
            String[] raw = new String[] { lines[0], lines[1], lines[2] };
            Satellite satellite = new Satellite(raw, "/statics/orekit-data");

            // 是否过境检测示例
            boolean isToTopRes = satellite.isToTop("2025-10-11 15:24:41", new Double[] { 120.0, 40.0, 0.0 }, 20.0);
            // 生成czml路径文件示例
            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC);
            ZonedDateTime end = start.plusHours(48);
            String czmlDocString = satellite.getCzmlDocString(start, end, Optional.empty());
            String toTopEventsString = satellite.toTopEventDetect(satellite.strToAbsoluteDate("2025-10-11 15:24:41"),
                    satellite.strToAbsoluteDate("2026-12-13 15:24:41"), new Double[] { 120.0, 40.0, 0.0 }, 200.0);
            try {
                // 将生成的czml写入json文件
                Files.writeString(java.nio.file.Path.of("out.json"), czmlDocString);
                Files.writeString(java.nio.file.Path.of("toTopEvents.json"), toTopEventsString);
                System.out.println(isToTopRes);
            } catch (Exception e) {
                System.out.println(e);
            }
        } catch (Exception e) {
            System.out.println(e);
        }

    }

}
