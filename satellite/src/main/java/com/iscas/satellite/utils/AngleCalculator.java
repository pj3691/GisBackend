package com.iscas.satellite.utils;

import java.lang.Math;

/**
 * 角度计算器类，提供从地理坐标（LLA）到地心地固坐标（ECEF）的转换，
 * 并支持计算地面点到空中点之间的方位角和俯仰角。
 * <p>
 * 使用说明：
 * 输入参数：
 * 地面点：纬度、经度、高度（米）
 * 空中点：纬度、经度、高度（米）
 * 输出结果：
 * 方位角：从正北方向顺时针旋转的角度（0-360度）
 * 俯仰角：从水平面向上为正的角度（-90到+90度）
 * 可选：两点之间的直线距离
 * 计算方法：
 * 使用WGS84地球椭球模型进行精确计算
 * 先将经纬高转换为ECEF（地心地固）坐标系
 * 在本地ENU（东北天）坐标系中计算向量
 * 使用atan2函数确保正确的象限处理
 * 精度：
 * 计算结果考虑了地球曲率和高度影响
 * 适用于航空、导航、雷达定位等应用场景
 * </p>
 */
public class AngleCalculator {
    // WGS84椭球参数
    private static final double EARTH_SEMI_MAJOR = 6378137.0; // 地球半长轴 (米)
    private static final double FLATTENING = 1.0 / 298.257223563; // 扁率
    private static final double ECC_SQUARED = 2 * FLATTENING - FLATTENING * FLATTENING; // 偏心率平方

    /**
     * 将经纬度高程坐标（LLA）转换为地心地固坐标（ECEF）
     *
     * @param latitude  纬度（单位：度）
     * @param longitude 经度（单位：度）
     * @param altitude  高程（单位：米）
     * @return 返回包含ECEF坐标的数组 [x, y, z]
     */
    public static double[] llaToEcef(double latitude, double longitude, double altitude) {
        double latRad = Math.toRadians(latitude);
        double lonRad = Math.toRadians(longitude);

        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);
        double sinLon = Math.sin(lonRad);
        double cosLon = Math.cos(lonRad);

        // 计算卯酉圈曲率半径
        double N = EARTH_SEMI_MAJOR / Math.sqrt(1 - ECC_SQUARED * sinLat * sinLat);

        double x = (N + altitude) * cosLat * cosLon;
        double y = (N + altitude) * cosLat * sinLon;
        double z = (N * (1 - ECC_SQUARED) + altitude) * sinLat;

        return new double[] { x, y, z };
    }

    /**
     * 计算从地面点到空中点的向量在ENU坐标系中的分量
     *
     * @param groundLat 地面点纬度（单位：度）
     * @param groundLon 地面点经度（单位：度）
     * @param groundAlt 地面点高度（单位：米）
     * @param airLat    空中点纬度（单位：度）
     * @param airLon    空中点经度（单位：度）
     * @param airAlt    空中点高度（单位：米）
     * @return 返回ENU坐标系下的向量数组 [east, north, up]
     */
    public static double[] calculateEnuVector(
            double groundLat, double groundLon, double groundAlt,
            double airLat, double airLon, double airAlt) {

        // 转换为ECEF坐标
        double[] groundEcef = llaToEcef(groundLat, groundLon, groundAlt);
        double[] airEcef = llaToEcef(airLat, airLon, airAlt);

        // 计算ECEF坐标系中的向量
        double dx = airEcef[0] - groundEcef[0];
        double dy = airEcef[1] - groundEcef[1];
        double dz = airEcef[2] - groundEcef[2];

        // 地面点经纬度（弧度）
        double groundLatRad = Math.toRadians(groundLat);
        double groundLonRad = Math.toRadians(groundLon);

        double sinLat = Math.sin(groundLatRad);
        double cosLat = Math.cos(groundLatRad);
        double sinLon = Math.sin(groundLonRad);
        double cosLon = Math.cos(groundLonRad);

        // 计算ENU分量
        double east = -sinLon * dx + cosLon * dy;
        double north = -sinLat * cosLon * dx - sinLat * sinLon * dy + cosLat * dz;
        double up = cosLat * cosLon * dx + cosLat * sinLon * dy + sinLat * dz;

        return new double[] { east, north, up };
    }

    /**
     * 根据东向和北向分量计算方位角
     *
     * @param east  东向分量
     * @param north 北向分量
     * @return 方位角（单位：度，范围 0~360）
     */
    public static double calculateAzimuth(double east, double north) {
        double azimuthRad = Math.atan2(east, north);
        double azimuthDeg = Math.toDegrees(azimuthRad);

        // 确保结果在0-360度范围内
        if (azimuthDeg < 0) {
            azimuthDeg += 360.0;
        }
        return azimuthDeg;
    }

    /**
     * 根据东向、北向和天向分量计算俯仰角
     *
     * @param east  东向分量
     * @param north 北向分量
     * @param up    天向分量
     * @return 俯仰角（单位：度）
     */
    public static double calculateElevation(double east, double north, double up) {
        double horizontal = Math.sqrt(east * east + north * north);
        double elevationRad = Math.atan2(up, horizontal);
        return Math.toDegrees(elevationRad);
    }

    /**
     * 将经纬度高程坐标（LLA）转换为东北天（ENU）坐标系
     *
     * @param refLat    参考点纬度（单位：度）
     * @param refLon    参考点经度（单位：度）
     * @param refAlt    参考点高度（单位：米）
     * @param targetLat 目标点纬度（单位：度）
     * @param targetLon 目标点经度（单位：度）
     * @param targetAlt 目标点高度（单位：米）
     * @return 返回ENU坐标系下的坐标数组 [east, north, up]
     */
    public static double[] llaToEnu(
            double refLat, double refLon, double refAlt,
            double targetLat, double targetLon, double targetAlt) {

        // 复用现有的calculateEnuVector方法
        return calculateEnuVector(refLat, refLon, refAlt, targetLat, targetLon, targetAlt);
    }

    public static void main(String[] args) {
        // 示例坐标
        double groundLat = 40.0; // 地面点纬度（度）
        double groundLon = 116.0; // 地面点经度（度）
        double groundAlt = 100.0; // 地面点高度（米）
        System.out.println("Ground Latitude: " + groundLat);
        System.out.println("Ground Longitude: " + groundLon);
        System.out.println("Ground Altitude: " + groundAlt);
        double airLat = 40.5; // 空中点纬度（度）
        double airLon = 116.5; // 空中点经度（度）
        double airAlt = 10000.0; // 空中点高度（米）

        // System.out.println("Air Latitude: " + airLat);
        // System.out.println("Air Longitude: " + airLon);
        // System.out.println("Air Altitude: " + airAlt);
        // // 计算ENU向量
        // double[] enu = calculateEnuVector(groundLat, groundLon, groundAlt, airLat,
        // airLon, airAlt);
        // double east = enu[0];
        // double north = enu[1];
        // double up = enu[2];

        // System.out.println("东向分量: " + east);
        // System.out.println("北向分量: " + north);
        // System.out.println("天向分量: " + up);
        // // 计算角度
        // double azimuth = calculateAzimuth(east, north);
        // double elevation = calculateElevation(east, north, up);

        // // 输出结果
        // System.out.println("方位角 (Azimuth): " + String.format("%.4f", azimuth) + "
        // 度");
        // System.out.println("俯仰角 (Elevation): " + String.format("%.4f", elevation) + "
        // 度");

        // // 可选：计算距离
        // double distance = Math.sqrt(east * east + north * north + up * up);
        // System.out.println("直线距离: " + String.format("%.2f", distance) + " 米");

        double[] equipEcef = llaToEcef(groundLat, groundLon, groundAlt);
        double[] targetEcef = llaToEcef(airLat, airLon, airAlt);
        Vector3 equipToTargVector3 = new Vector3(targetEcef[0] - equipEcef[0], targetEcef[1] - equipEcef[1],
                targetEcef[2] - equipEcef[2]);
        Vector3 xAxis = new Vector3(1, 0, 0);
        Vector3 zAxis = new Vector3(0, 0, 1);
        double distance = equipToTargVector3.length();
        /** 天顶角 */
        double zenithAngle = 90 - Math
                .toDegrees(Math.acos(equipToTargVector3.dot(zAxis) / (equipToTargVector3.length() * zAxis.length())));
        /** 方位角 */
        double azimuthAngle = Math
                .toDegrees(Math.acos(equipToTargVector3.dot(xAxis) / (equipToTargVector3.length() * xAxis.length())));
    }
}

class Vector3 {
    public double x, y, z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // 向量加法
    public Vector3 add(Vector3 v) {
        return new Vector3(x + v.x, y + v.y, z + v.z);
    }

    // 向量减法
    public Vector3 sub(Vector3 v) {
        return new Vector3(x - v.x, y - v.y, z - v.z);
    }

    // 点积
    public double dot(Vector3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    // 叉积
    public Vector3 cross(Vector3 v) {
        return new Vector3(
                y * v.z - z * v.y,
                z * v.x - x * v.z,
                x * v.y - y * v.x);
    }

    // 长度
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    // 归一化
    public Vector3 normalize() {
        double len = length();
        return len == 0 ? new Vector3(0, 0, 0) : new Vector3(x / len, y / len, z / len);
    }
}