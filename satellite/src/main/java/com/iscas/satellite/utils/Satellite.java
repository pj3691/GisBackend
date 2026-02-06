package com.iscas.satellite.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.math3.util.FastMath;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
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
import org.orekit.propagation.events.ExtremumApproachDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.hipparchus.ode.events.Action;
import org.orekit.utils.Constants;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.events.EventSlopeFilter;
import org.orekit.propagation.events.FilterType;

/**
 * 卫星类
 */
public class Satellite {
    /** 卫星源tle文件 */
    public String[] rawTle;
    /** 构造的orikit tle对象 */
    public TLE tleObject;
    /** 卫星的传播器，用于计算轨道 */
    public TLEPropagator propagator;
    /** 卫星名称 */
    public String satName;
    /** 卫星周期 */
    public double orbitalMinutes;
    /** 卫星轨道高度 */
    public double orbitalAltitude;

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

            SpacecraftState state = this.propagator.getInitialState();
            this.orbitalAltitude = state.getPVCoordinates().getPosition().getNorm()
                    - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load orekit-data from classpath", e);
        }
    }

    // === 时间转换相关方法 =================
    /**
     * 字符串转为AbsoluteDate
     * 
     * @param dateStr 时间字符串，eg.2025-09-01 03:57:01
     * @return
     */
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
        return new AbsoluteDate(new DateComponents(year, month, day), new TimeComponents(hour, minute, second), utc);
    }

    /**
     * 字符串转为ZonedDateTime
     * 
     * @param timeStr 时间字符串，eg.2025-09-01 03:57:01
     * @return
     */
    public ZonedDateTime parseUtcZonedDateTime(String timeStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDateTime localDateTime = LocalDateTime.parse(timeStr, formatter);

        return ZonedDateTime.of(localDateTime, ZoneOffset.UTC);
    }

    /**
     * 字符串转为ZonedDateTime
     * 
     * @param timeStr 时间字符串，eg.2025-09-01 03:57:01
     * @return
     * 
     */
    public ZonedDateTime dateStringToZonedDateTime(String timeStr) {
        // 1. 解析成 LocalDateTime（没有时区信息）
        LocalDateTime localDateTime = LocalDateTime.parse(timeStr.substring(0, 29),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 2. 指定时区，得到 ZonedDateTime
        return ZonedDateTime.of(localDateTime, ZoneOffset.UTC);
    }

    /**
     * ZonedDateTime转AbsoluteDate
     * 
     * 
     * @param dateTime
     * @return
     */
    public AbsoluteDate getAbsDate(ZonedDateTime dateTime) {
        return new AbsoluteDate(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(),
                dateTime.getHour(), dateTime.getMinute(),
                dateTime.getSecond() + dateTime.getNano() * 1.0e-9, TimeScalesFactory.getUTC());
    }
    // === 时间转换相关方法 =================

    /**
     * 获取某个时间段卫星的路径czml
     * 
     * @param start        开始时间
     * 
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

    class TimeValue {
        /** 过境时卫星经度 */
        public final double lon;
        /** 过境时卫星纬度 */
        public final double lat;
        /** 过境时卫星高 */
        public final double h;
        /** 过境时间 */
        public final String time;
        /** 过境时仰角 */
        public final double elevation;
        /** 升/降轨状态 */
        public final String state;
        /** 过境时太阳角 */
        public final double sunAngle;
        /** 过境时月亮角 */
        public final double moonAngle;

        public TimeValue(double lon, double lat, double h, String time, double elevation, String state, double sunAngle,
                double moonAngle) {
            this.lon = lon;
            this.lat = lat;
            this.h = h;
            this.time = time;
            this.elevation = elevation;
            this.state = state;

            this.sunAngle = sunAngle;
            this.moonAngle = moonAngle;
        }
    }

    // === 事件检测相关方法 =================

    /**
     * 检测卫星过境事件
     * 
     * @param startAbsDate 过境计算开始时间
     * @param endAbsDate   过境计算结束时间
     * @param longitude    计算目标位置的经度
     * @param latitude     计算目标位置的纬度
     * @param altitude     计算目标位置的高
     * @param minElevation 过境最小仰角
     * @param maxCheck     过境计算迭代间隔（单位为秒，间隔越小迭代次数越多，计算所用时间越长）
     * @param threshold    过境计算误差范围
     * @return
     */
    public List<TimeValue> calculateTransits(AbsoluteDate startAbsDate, AbsoluteDate endAbsDate, double longitude,
            double latitude, double altitude,
            double minElevation, double maxCheck, double threshold) {
        List<TimeValue> transitTimes = new ArrayList<>();

        // 定义观察者的位置
        GeodeticPoint observerPoint = new GeodeticPoint(Math.toRadians(latitude), Math.toRadians(longitude), altitude);
        OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING,
                FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        TopocentricFrame observerFrame = new TopocentricFrame(earth, observerPoint, "Observer");
        final Frame earthFrame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);

        // 定义太阳月亮及观察者ITRF位置
        CelestialBody sun = CelestialBodyFactory.getSun();
        CelestialBody moon = CelestialBodyFactory.getMoon();
        PVCoordinates stationITRF = new PVCoordinates(earth.transform(observerPoint));

        // 构造ElevationDetector
        ElevationDetector elevationDetector = new ElevationDetector(maxCheck, threshold, observerFrame)
                .withConstantElevation(Math.toRadians(minElevation))
                .withHandler((s, detector, increasing) -> {
                    PVCoordinates pvCoordinates = s.getPVCoordinates(earthFrame);
                    PVCoordinates stationPosPV = earth.getBodyFrame()
                            .getTransformTo(FramesFactory.getGCRF(), s.getDate())

                            .transformPVCoordinates(stationITRF);
                    Vector3D sunPos = sun.getPVCoordinates(s.getDate(), earthFrame).getPosition();

                    Vector3D moonPos = moon.getPVCoordinates(s.getDate(), earthFrame).getPosition();
                    Vector3D satPos = pvCoordinates.getPosition();
                    Vector3D stationPos = stationPosPV.getPosition();
                    Vector3D stationToSun = sunPos.subtract(stationPos);
                    Vector3D stationToMoon = moonPos.subtract(stationPos);
                    Vector3D stationToSat = satPos.subtract(stationPos);
                    double sunAngle = FastMath.toDegrees(Vector3D.angle(stationToSat, stationToSun));
                    double moonAngle = FastMath.toDegrees(Vector3D.angle(stationToSat, stationToMoon));

                    GeodeticPoint geodeticPoint = earth.transform(pvCoordinates.getPosition(), earthFrame, s.getDate());
                    double lat = FastMath.toDegrees(geodeticPoint.getLatitude());
                    double lon = FastMath.toDegrees(geodeticPoint.getLongitude());
                    double h = FastMath.toDegrees(geodeticPoint.getAltitude());
                    Double ele = FastMath.toDegrees(
                            observerFrame.getElevation(pvCoordinates.getPosition(), earthFrame, s.getDate()));
                    AbsoluteDate date = s.getDate();
                    String dateString = date.toString(TimeScalesFactory.getUTC());
                    transitTimes.add(
                            new TimeValue(lon, lat, h, dateString, ele, increasing ? "升轨" : "降轨", sunAngle, moonAngle));
                    return Action.CONTINUE;
                });

        this.propagator.addEventDetector(elevationDetector);

        // 传播卫星状态并检测事件
        this.propagator.propagate(startAbsDate, endAbsDate);

        return transitTimes;
    }

    /**
     * 检测与另一个卫星的交会时间
     * 
     * @param startAbsDate         检测开始时间
     * @param endAbsDate           检测结束时间
     * @param satellite            另一个卫星实例
     * @param minDistanceThreshold 最小检测距离
     * @param maxDistanceThreshold 最大检测距离
     * @return 检测到的交会时间
     */
    public ZonedDateTime getApproachEvent(AbsoluteDate startAbsDate, AbsoluteDate endAbsDate, Satellite satellite,
            double minDistanceThreshold,
            double maxDistanceThreshold) {
        ExtremumApproachDetector approachDetector = new ExtremumApproachDetector(satellite.propagator)
                .withThreshold(1.0e-3);

        approachDetector = approachDetector.withHandler(new EventHandler() {
            @Override
            public Action eventOccurred(SpacecraftState s, EventDetector detector, boolean increasing) {
                PVCoordinates pvSat1 = s.getPVCoordinates();
                PVCoordinates pvSat2 = satellite.propagator.propagate(s.getDate()).getPVCoordinates();
                double distance = pvSat1.getPosition().distance(pvSat2.getPosition());
                if (distance < maxDistanceThreshold && distance > minDistanceThreshold) {
                    // 满足距离阈值，触发事件
                    return Action.STOP;
                }
                return Action.CONTINUE;
            }
        });

        EventDetector closeApproachDetector = new EventSlopeFilter<ExtremumApproachDetector>(approachDetector,
                FilterType.TRIGGER_ONLY_INCREASING_EVENTS);

        // 注册探测器
        this.propagator.addEventDetector(closeApproachDetector);

        // 执行传播
        SpacecraftState state = this.propagator.propagate(startAbsDate, endAbsDate);

        // System.out.println(state.getDate());
        return ZonedDateTime.ofInstant(state.getDate().toDate(TimeScalesFactory.getUTC()).toInstant(), ZoneOffset.UTC);
    }
    // === 事件检测相关方法 =================

    public static void main(String[] args) {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("classpath:/statics/tles/STARLINK.tle");
        Resource resource2 = resourceLoader.getResource("classpath:/statics/tles/ISS.tle");
        Resource resource3 = resourceLoader.getResource("classpath:/statics/tles/GF-7.tle");
        try (InputStream is = resource.getInputStream();
                InputStream is2 = resource2.getInputStream();
                InputStream is3 = resource3.getInputStream()) {
            // 构造卫星1
            String te1 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = te1.split("\n");
            String[] raw = new String[] { lines[0], lines[1], lines[2] };
            Satellite satellite = new Satellite(raw, "/statics/orekit-data");

            // 构造卫星2
            String te2 = new String(is2.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines2 = te2.split("\n");
            String[] raw2 = new String[] { lines2[0], lines2[1], lines2[2] };
            Satellite satellite2 = new Satellite(raw2, "/statics/orekit-data");

            // 构造卫星3
            String te3 = new String(is3.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines3 = te3.split("\n");
            String[] raw3 = new String[] { lines3[0], lines3[1], lines3[2] };
            Satellite satellite3 = new Satellite(raw3, "/statics/orekit-data");

            // 计算卫星1和卫星2交会的时间
            long minute = -1;
            AbsoluteDate startAbsDate = satellite.getAbsDate(satellite.parseUtcZonedDateTime("2020-09-01 03:57:01"));
            AbsoluteDate endAbsDate = satellite.getAbsDate(satellite.parseUtcZonedDateTime("2028-10-01 03:57:01"));
            ZonedDateTime approachEventStart = satellite
                    .getApproachEvent(startAbsDate, endAbsDate, satellite2, 0,
                            Math.abs(satellite.orbitalAltitude - satellite2.orbitalAltitude))
                    .plusMinutes(minute);
            ZonedDateTime approachEventEnd = approachEventStart.plusHours(4);
            // 根据交会时间生成对应的czml
            String czmlDocString = satellite.getCzmlDocString(approachEventStart, approachEventEnd, Optional.empty());
            String czmlDocString2 = satellite2.getCzmlDocString(approachEventStart, approachEventEnd, Optional.empty());

            // 计算卫星3过境时间
            AbsoluteDate start2 = satellite.getAbsDate(satellite.parseUtcZonedDateTime("2025-09-04 00:00:00"));
            AbsoluteDate end2 = satellite.getAbsDate(satellite.parseUtcZonedDateTime("2025-09-13 00:00:00"));
            List<TimeValue> toTopRes = satellite3.calculateTransits(start2, end2, 109.62069474947575,
                    18.327190840495074, 0, 80, 1,
                    1e-5);
            ZonedDateTime toTopDateStart = satellite.dateStringToZonedDateTime(toTopRes.get(0).time);
            ZonedDateTime toTopDateEnd = toTopDateStart.plusDays(1);
            // 根据过境时间生成对应的czml
            String czmlDocString3 = satellite3.getCzmlDocString(toTopDateStart, toTopDateEnd, Optional.empty());

            try {
                // 创建输出目录 - 相对于项目根目录
                // java.nio.file.Path outputDir =
                // java.nio.file.Path.of("satellite/src/main/resources/statics/czmlOut");
                java.nio.file.Path outputDir = java.nio.file.Path.of("D:/ProgramFiles/nginx-1.26.0/html/czml");
                if (!Files.exists(outputDir)) {
                    Files.createDirectories(outputDir);
                }

                // 将生成的czml写入json文件
                Files.writeString(outputDir.resolve("approach1.czml"), czmlDocString);
                Files.writeString(outputDir.resolve("approach2.czml"), czmlDocString2);
                Files.writeString(outputDir.resolve("toTop1.czml"), czmlDocString3);
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT); // 美化输出
                mapper.writeValue(outputDir.resolve("toTop1.json").toFile(), toTopRes);
            } catch (Exception e) {
                System.out.println(e);
            }
        } catch (Exception e) {
            System.out.println(e);
        }

    }

}
