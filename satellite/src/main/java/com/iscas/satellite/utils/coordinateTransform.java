package com.iscas.satellite.utils;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

public class coordinateTransform {
    static double a = 6378137.0;
    static double b = 6356752.3141;

    public static void main(String[] args) throws Exception {
        // double[] values = LBHToXYZ(140, 50, 80);

        // System.out.println(values[0]);
        // System.out.println(values[1]);
        // System.out.println(values[2]);

        // double[] values2 = XYZToLBH(values[0], values[1], values[2]);

        // System.out.println(values2[0]);
        // System.out.println(values2[1]);
        // System.out.println(values2[2]);

        // double[] values3 = velocityTransform(values[0], values[1], values[2], 200,
        // 400, 500);

        // System.out.println(values3[0]);
        // System.out.println(values3[1]);
        // System.out.println(values3[2]);

        double[] LBH = new double[] { 108.0831, 26.305827255287856, 566.0608400131235 };
        double[][] result = new double[60][];
        double[][] result2 = new double[60][];

        for (int i = 0; i < 60; i++) {
            LBH[0] += 0.00001;
            LBH[1] += 0.00001;
            LBH[2] += 5;
            double[] values1 = LBHToXYZ(LBH[0], LBH[1], LBH[2]);
            result[i] = values1;
            double[] values2 = XYZToLBH(values1[0], values1[1], values1[2]);
            result2[i] = values2;
        }

        for (int i = 0; i < result2.length; i++) {
            System.out
                    .println("Result " + i + ": X=" + result2[i][0] + ", Y=" + result2[i][1] + ", Z=" + result2[i][2]);
        }
    }

    public static double sin(double x) {
        return Math.sin(Math.toRadians(x));
    }

    public static double cos(double x) {
        return Math.cos(Math.toRadians(x));
    }

    public static double tan(double x) {
        return Math.tan(Math.toRadians(x));
    }

    public static double atan(double x) {
        return Math.toDegrees(Math.atan(x));
    }

    public static double acos(double x) {
        return Math.toDegrees(Math.acos(x));
    }

    /**
     * 经纬高转XYZ
     * 
     * @param L 经度
     * @param B 纬度
     * @param H 高度
     * @return double[] [X,Y,Z]
     */
    public static double[] LBHToXYZ(double L, double B, double H) {

        double[] values = new double[3];

        double e1 = Math.sqrt((Math.pow(a, 2) - Math.pow(b, 2))) / a;

        double N = a / Math.sqrt(1.0 - Math.pow(e1, 2) * Math.pow(sin(B), 2));

        double X = (N + H) * cos(B) * cos(L);
        double Y = (N + H) * cos(B) * sin(L);
        double Z = (N * (1.0 - Math.pow(e1, 2)) + H) * sin(B);

        values[0] = X;
        values[1] = Y;
        values[2] = Z;
        return values;
    }

    /**
     * XYZ转经纬高
     * 
     * @param X 地心坐标X
     * @param Y 地心坐标Y
     * @param Z 地心坐标Z
     * @return double[] [经度,纬度,高度]
     */
    public static double[] XYZToLBH(double X, double Y, double Z) {
        double[] values = new double[3];

        double e1 = (Math.pow(a, 2) - Math.pow(b, 2)) / Math.pow(a, 2);
        double e2 = (Math.pow(a, 2) - Math.pow(b, 2)) / Math.pow(b, 2);

        double S1 = Math.sqrt(Math.pow(X, 2) + Math.pow(Y, 2));
        double cosL = X / S1;
        double B = 0;
        double L = 0;

        L = Math.acos(cosL);
        L = Math.abs(L);

        double tanB = Z / S1;
        B = Math.atan(tanB);
        double C = a * a / b;
        double preB = 0.0;
        double ll = 0.0;
        double N = 0.0;

        do {
            preB = B;
            ll = Math.pow(Math.cos(B), 2) * e2;
            N = C / Math.sqrt(1 + ll);

            tanB = (Z + N * e1 * Math.sin(B)) / S1;
            B = Math.atan(tanB);
        } while (Math.abs(B - preB) > 0.0000000000001);

        ll = Math.pow(Math.cos(B), 2) * e2;
        N = C / Math.sqrt(1 + ll);

        // 结果
        double TargetL = Math.toDegrees(L);

        double TargetB = Math.toDegrees(B);

        double TargetH = (S1 / Math.cos(B)) - N;

        values[0] = TargetL;
        values[1] = TargetB;
        values[2] = TargetH;

        return values;
    }

    /**
     * XYZ速度转东北天速度
     * 
     * @param X  地心坐标X
     * @param Y  地心坐标Y
     * @param Z  地心坐标Z
     * @param Vx X方向速度
     * @param Vy Y方向速度
     * @param Vz Z方向速度
     * @return double[] [东速度,北向速度,垂向速度]
     */
    public static double[] velocityTransform(double X, double Y, double Z, double Vx, double Vy, double Vz) {
        double[] values = new double[3];

        double LBH[] = XYZToLBH(X, Y, Z);

        double Lon = LBH[0];
        double Lat = LBH[1];

        RealMatrix transformMatrix = new Array2DRowRealMatrix(new double[][] {
                { -sin(Lon), cos(Lat), 0, },
                { -sin(Lat) * cos(Lon), -sin(Lat) * sin(Lon), cos(Lat), },
                { cos(Lat) * cos(Lon), cos(Lat) * sin(Lon), sin(Lat), },
        });

        RealMatrix velocityMatrix = new Array2DRowRealMatrix(new double[][] { { Vx }, { Vy }, { Vz } })
                .transpose();

        RealMatrix resultMatrix = velocityMatrix.multiply(transformMatrix);

        values[0] = resultMatrix.getEntry(0, 0);
        values[1] = resultMatrix.getEntry(0, 1);
        values[2] = resultMatrix.getEntry(0, 2);

        return values;
    }
}
