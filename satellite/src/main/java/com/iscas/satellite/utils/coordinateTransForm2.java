package com.iscas.satellite.utils;

public class coordinateTransForm2 {
    static double a = 6378137.0;
    static double b = 6356752.3141;
    static double c = 6399593.6359;
    static double e2 = 0.00669438002290;
    static double e12 = 0.00673949677548;

    public static void main(String[] args) throws Exception {
        double[] values = LBHToXYZ(120, 50, 2000);

        System.out.println(values[0]);
        System.out.println(values[1]);
        System.out.println(values[2]);

        double[] values2 = XYZToLBH(values[0], values[1], values[2]);

        System.out.println(values2[0]);
        System.out.println(values2[1]);
        System.out.println(values2[2]);
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

    public static double[] LBHToXYZ(double L, double B, double H) {

        double[] values = new double[3];

        double N = a / Math.sqrt(1 - e2 * Math.pow(sin(B), 2));

        double X = (N + H) * cos(B) * cos(L);
        double Y = (N + H) * cos(B) * sin(L);
        double Z = (N * (1 - e2) + H) * sin(B);

        values[0] = X;
        values[1] = Y;
        values[2] = Z;
        return values;
    }

    public static double[] XYZToLBH(double X, double Y, double Z) {
        double[] values = new double[3];

        double anglesita = atan(Z * a / Math.sqrt(X * X + Y * Y) * b);

        // 结果
        double L = atan(Y / X) < 0 ? atan(Y / X) + 180 : atan(Y / X);

        double B = atan((Z + e12 * b * Math.pow(sin(anglesita), 3))
                / (Math.sqrt(X * X + Y * Y) - e2 * a * Math.pow(cos(anglesita), 3)));

        double N = a / Math.sqrt(1 - e2 * Math.pow(sin(B), 2));

        double H = Math.sqrt(X * X + Y * Y) / cos(B) - N * (1 - e2);

        values[0] = L;
        values[1] = B;
        values[2] = H;

        return values;
    }

}
