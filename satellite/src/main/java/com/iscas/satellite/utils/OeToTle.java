package com.iscas.satellite.utils;

import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.Relativity;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.frames.Transform;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

/**
 * oe转为tle
 */
public class OeToTle {

    /**
     * 将真近点角转换为平近点角
     * 
     * @param trueAnomaly 真近点角 (弧度)
     * @param e           偏心率
     * @return 平近点角 (弧度)
     */
    public static double trueAnomalyToMeanAnomaly(double trueAnomaly, double e) {
        // 1. 真近点角 → 偏近点角
        double eccentricAnomaly = trueAnomalyToEccentricAnomaly(trueAnomaly, e);

        // 2. 偏近点角 → 平近点角
        double meanAnomaly = eccentricAnomalyToMeanAnomaly(eccentricAnomaly, e);

        return meanAnomaly;
    }

    /**
     * 真近点角 → 偏近点角
     */
    private static double trueAnomalyToEccentricAnomaly(double trueAnomaly, double e) {
        // 使用公式: cos(E) = (e + cos(ν)) / (1 + e * cos(ν))
        double cosTrueAnomaly = Math.cos(trueAnomaly);
        double cosE = (e + cosTrueAnomaly) / (1.0 + e * cosTrueAnomaly);

        // 处理数值精度问题，确保cos值在[-1,1]范围内
        cosE = Math.max(-1.0, Math.min(1.0, cosE));

        double eccentricAnomaly = Math.acos(cosE);

        // 根据真近点角所在的象限调整偏近点角的符号
        if (trueAnomaly > Math.PI) {
            eccentricAnomaly = 2 * Math.PI - eccentricAnomaly;
        }

        return eccentricAnomaly;
    }

    /**
     * 偏近点角 → 平近点角
     */
    private static double eccentricAnomalyToMeanAnomaly(double eccentricAnomaly, double e) {
        // 开普勒方程: M = E - e * sin(E)
        return eccentricAnomaly - e * Math.sin(eccentricAnomaly);
    }

    /**
     * 从半长轴计算平均运动 (弧度/秒)
     * 
     * @param a 半长轴 (米)
     * @return 平均运动 (弧度/秒)
     */
    public static double semiMajorAxisToMeanMotionRad(double a) {
        if (a <= 0) {
            throw new IllegalArgumentException("Semi-major axis must be positive");
        }
        return Math.sqrt(Constants.WGS84_EARTH_MU / (a * a * a));
    }

    public static void main(String[] args) throws Exception {

        String dataName = "./input.txt";
        File file = new File(dataName);// 设定为当前文件夹
        if (!file.exists()) {
            System.out.println("当前目录下无input.txt文件");
            return;
        }
        FileInputStream inputStream = new FileInputStream(file);
        Scanner scanner = new Scanner(inputStream);
        String line0 = scanner.nextLine();
        String line1 = scanner.nextLine();
        String line2 = scanner.nextLine();
        String line3 = scanner.nextLine();
        if (line0 == null || line1 == null || line2 == null || line3 == null) {
            System.out.println("input.txt中小于4行");
            return;
        }

        // 设置Orekit数据目录（包含earth-orientation-parameters、MSC等数据）
        File orekitData = new File("orekit-data-main");
        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
        manager.addProvider(new DirectoryCrawler(orekitData));

        Frame inertialFrame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        Frame inertialFrame2 = FramesFactory.getEME2000();

        OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING, inertialFrame);

        // 4. 计算未来24小时每1分钟的仰角
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String[] times = line3.split(",");
        Date startTime = sdf.parse(times[0]);
        startTime.setTime(startTime.getTime() - 8 * 60 * 60 * 1000);// 北京时转utc
        Date stopTime = sdf.parse(times[1]);
        stopTime.setTime(stopTime.getTime() - 8 * 60 * 60 * 1000);// 北京时转utc
        // AbsoluteDate start = new AbsoluteDate(2025, 3, 25, 0, 40, 57.0,
        // TimeScalesFactory.getUTC());//tle.getDate();
        AbsoluteDate start = new AbsoluteDate(startTime.getYear() + 1900, startTime.getMonth() + 1, startTime.getDate(),
                startTime.getHours(), startTime.getMinutes(), startTime.getSeconds(), TimeScalesFactory.getUTC());// tle.getDate();

        AbsoluteDate end = new AbsoluteDate(stopTime.getYear() + 1900, stopTime.getMonth() + 1, stopTime.getDate(),
                stopTime.getHours(), stopTime.getMinutes(), stopTime.getSeconds(), TimeScalesFactory.getUTC());
        Double step = Double.parseDouble(times[2]);

        // 六根数参数
        double a = 6939377.7; // 半长轴（米）
        double e = 0.00145295; // 偏心率
        double i = Math.toRadians(97.41997); // 倾角（弧度）
        double omega = Math.toRadians(125.1906); // 近地点幅角（弧度）
        double raan = Math.toRadians(271.2763); // 升交点赤经（弧度）
        double nu = Math.toRadians(278.668); // 真近点角（弧度）

        KeplerianOrbit initialOrbit = new KeplerianOrbit(
                a, e, i, omega, raan, nu,
                PositionAngleType.TRUE,
                inertialFrame2,
                start,
                Constants.WGS84_EARTH_MU);

        // 5. 定义 TLE 所需的额外参数
        // 1. 设置 TLE 参数
        int satelliteNumber = 6005; // 卫星编号 (国际空间站)
        char classification = 'U'; // 分类级别: U-非密, C-机密, S-秘密
        int launchYear = 98; // 发射年份后两位 (1998)
        int launchNumber = 67; // 发射编号
        String launchPiece = "A"; // 发射部件标识
        int ephemerisType = 0; // 星历类型: 0-西方式, 1-增强型
        int elementNumber = 999; // 元素集编号

        // 历元时间: 2024年1月1日12:00:00 UTC
        // AbsoluteDate epoch = new AbsoluteDate(2024, 1, 1, 12, 0, 0,
        // TimeScalesFactory.getUTC());

        double meanMotion = semiMajorAxisToMeanMotionRad(a);// 15.72125391; // 平均运动 (圈/天)
        double meanMotionFirstDerivative = 0.0;// 0.00000001; // 平均运动一阶导数
        double meanMotionSecondDerivative = 0.0; // 平均运动二阶导数

        double meanAnomaly = Math.toRadians(trueAnomalyToMeanAnomaly(nu, e)); // 平近点角 (弧度)
        int revolutionNumberAtEpoch = 45678; // 历元时刻运行圈数
        double bStar = 0.00031049; // 阻力系数 B* (1/地球半径)
        TimeScale utc = TimeScalesFactory.getUTC(); // UTC时间系统

        // 2. 创建 TLE 对象
        TLE tle = new TLE(
                satelliteNumber,
                classification,
                launchYear,
                launchNumber,
                launchPiece,
                ephemerisType,
                elementNumber,
                start,
                meanMotion,
                meanMotionFirstDerivative,
                meanMotionSecondDerivative,
                e,
                i,
                omega,
                raan,
                meanAnomaly,
                revolutionNumberAtEpoch,
                bStar,
                utc);

        System.out.println(tle.getLine1());
        System.out.println(tle.getLine2());

    }
}
