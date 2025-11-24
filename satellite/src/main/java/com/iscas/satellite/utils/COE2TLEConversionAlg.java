package com.iscas.satellite.utils;

/**  
 *  @Project       : testObject;
 *  @Program Name  : .COE2TLEConversion.java;
 *  @Class Name    : COE2TLEConversion;
 *  @Description   : ;
 *  @Author        : shilusha;
 *  @since Ver 1.0
 *  @Creation Date : 2017-5-3 下午4:45:15 ;
 *  @ModificationHistory  ;
 *  who        When          What ;
 *  --------   ----------    -----------------------------------;
 *  username   2017-5-3       TODO;
 */

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class COE2TLEConversionAlg {
    private static final double MU = 398600.4418;

    /**
     * ;
     * 
     * @Description :卫星开普勒六根数转换到两行轨道根数;
     * @Method_Name : simpleCoe2Tle;
     * @param startYear:开始年
     * @param startTime:开始时间，每年1月1日0点为0，后逐渐累积，整数部分为日，小数部分为时分秒
     * @param six                                             :轨道六根数,顺序为
     *                                                        Mean :平均运动（每日绕行圈数）
     *                                                        Eccentricity :离心率（小数）
     *                                                        Inclination:轨道的交角（deg）
     *                                                        Argument of
     *                                                        perigee:近地点角矩(deg)
     *                                                        RAAN :升交点赤经（deg）
     *                                                        Mean :在轨圈数
     * @param name                                            :卫星两位数编号，如01
     * @return :两行轨道根数
     * @return : String[];
     * @Creation Date : 2017-5-3 下午4:45:09 ;
     * @version : v1.0;
     * @Author : ;
     * @Update Date :
     * @Update Author : ;
     */
    ;

    public String[] simpleCoe2Tle(int startYear, double startTime, double[] six, String name) {
        String[] tle = new String[2];
        StringBuffer sBuffer0 = new StringBuffer("1 ");
        // 行号+卫星编号
        StringBuffer sBuffer1 = new StringBuffer("2 ");
        String sTemp;// 临时变量，存储six数组中转换后的String
        int length;// 临时变量，存储sTemp的长度

        sTemp = name;
        length = sTemp.length();
        if (length == 5) {
            sBuffer0.append(sTemp);
            sBuffer1.append(sTemp);
        } else if (length < 5) {
            for (int i = 0; i < 5 - length; i++) {
                sBuffer0.append("0");
                sBuffer1.append("0");
            }
            sBuffer0.append(sTemp);
            sBuffer1.append(sTemp);
        } else {
            System.out.println("satellite name error");
        }
        sBuffer0.append("U          " + startYear + Double.toString(startTime).substring(0, 12)
                + "  .00000000  00000-0  00000-0 0 0000");
        sBuffer1.append(" ");

        // 轨道的交角six[2]
        sTemp = Double.toString(six[2]);
        length = sTemp.length();
        if (six[2] < 10) {
            sBuffer1.append("00");

            if (length >= 7) {
                sBuffer1.append(sTemp.substring(0, 6));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 6 - length; i++) {
                    sBuffer1.append("0");
                }
            }

        } else if ((six[2] < 100) && (six[2] > 10)) {
            sBuffer1.append("0");
            if (length >= 8) {
                sBuffer1.append(sTemp.substring(0, 7));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 7 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        } else {
            if (length >= 9) {
                sBuffer1.append(sTemp.substring(0, 8));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 8 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        }
        sBuffer1.append(" ");

        // 升交点赤经six[4]
        sTemp = Double.toString(six[4]);
        length = sTemp.length();
        if (six[4] < 10) {
            sBuffer1.append("00");

            if (length >= 7) {
                sBuffer1.append(sTemp.substring(0, 6));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 6 - length; i++) {
                    sBuffer1.append("0");
                }
            }

        } else if ((six[4] < 100) && (six[4] > 10)) {
            sBuffer1.append("0");
            if (length >= 8) {
                sBuffer1.append(sTemp.substring(0, 7));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 7 - length; i++) {
                    sBuffer1.append("0");
                }
            }

        } else {
            if (length >= 9) {
                sBuffer1.append(sTemp.substring(0, 8));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 8 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        }
        sBuffer1.append(" ");

        // 离心率six[1]
        sTemp = Double.toString(six[1]);
        sTemp = sTemp.substring(2);
        length = sTemp.length();
        if (length >= 9) {
            sBuffer1.append(sTemp.substring(0, 7));
        } else {
            sBuffer1.append(sTemp);
            for (int i = 0; i < 7 - length; i++) {
                sBuffer1.append("0");
            }
        }
        sBuffer1.append(" ");

        // 近地点角矩
        sTemp = Double.toString(six[3]);
        length = sTemp.length();
        if (six[3] < 10) {
            sBuffer1.append("00");

            if (length >= 7) {
                sBuffer1.append(sTemp.substring(0, 6));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 6 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        } else if ((six[3] < 100) && (six[3] > 10)) {
            sBuffer1.append("0");
            if (length >= 8) {
                sBuffer1.append(sTemp.substring(0, 7));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 7 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        } else {
            if (length >= 9) {
                sBuffer1.append(sTemp.substring(0, 8));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 8 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        }
        sBuffer1.append(" ");

        // 在轨圈数
        sTemp = Double.toString(six[5]);
        length = sTemp.length();
        if (six[5] < 10) {
            sBuffer1.append("00");

            if (length >= 7) {
                sBuffer1.append(sTemp.substring(0, 6));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 6 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        } else if ((six[5] < 100) && (six[5] > 10)) {
            sBuffer1.append("0");
            if (length >= 8) {
                sBuffer1.append(sTemp.substring(0, 7));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 7 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        } else {
            if (length >= 9) {
                sBuffer1.append(sTemp.substring(0, 8));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 8 - length; i++) {
                    sBuffer1.append("0");
                }
            }
        }
        sBuffer1.append(" ");

        // 平均运动（每日绕行圈数）
        sTemp = Double.toString(six[0]);
        length = sTemp.length();
        if (six[0] < 10) {
            sBuffer1.append("0");
            if (length >= 16) {
                sBuffer1.append(sTemp.substring(0, 15));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 15 - length; i++) {
                    sBuffer1.append("0");
                }
            }
            // tle[1]=tle[1]+"0"+Double.toString(six[0]).substring(0, 14)+" ";
        } else {
            if (length >= 17) {
                sBuffer1.append(sTemp.substring(0, 16));
            } else {
                sBuffer1.append(sTemp);
                for (int i = 0; i < 16 - length; i++) {
                    sBuffer1.append("0");
                }
            }
            /* tle[1]=tle[1]+Double.toString(six[0]).substring(0, 15)+" "; */
        }

        tle[0] = sBuffer0.toString();
        tle[1] = sBuffer1.toString();
        // 第一行校验
        int sum = 0;
        for (int i = 0; i < tle[0].length(); i++) {
            String subTLE = tle[0].substring(i, i + 1);
            if ((subTLE.equals("U")) || (subTLE.equals(" ")) || (subTLE.equals(".")) || (subTLE.equals("+"))) {
                sum += 0;
            } else if ((subTLE.equals("-"))) {
                sum += 1;
            } else {
                sum = sum + Integer.valueOf(subTLE);
            }
        }

        // 第二行校验
        tle[0] = tle[0] + sum % 10;
        sum = 0;
        for (int i = 0; i < tle[1].length(); i++) {
            String subTLE = tle[1].substring(i, i + 1);
            if ((subTLE.equals("U")) || (subTLE.equals(" ")) || (subTLE.equals(".")) || (subTLE.equals("+"))) {

            } else if ((subTLE.equals("-"))) {
                sum += 1;
            } else {
                sum = sum + Integer.parseInt(subTLE);
            }
        }

        tle[1] = tle[1] + sum % 10;
        return tle;
    }

    /**
     * 将时间字符串解析为 年 和 年内累计天数(小数)
     * 
     * @param timeStr 格式: yyyy-MM-dd HH:mm:ss
     * @return double[]{startYear, startTime}
     */
    public static double[] parseTime(String timeStr) {
        // 1. 解析字符串
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dt = LocalDateTime.parse(timeStr, fmt);

        // 2. 提取年份
        int year = dt.getYear();

        // 3. 计算当年的 1月1日0点
        LocalDateTime yearStart = LocalDateTime.of(year, 1, 1, 0, 0, 0);

        // 4. 算相差的秒数
        long seconds = ChronoUnit.SECONDS.between(yearStart, dt);

        // 5. 转成天 + 小数
        double startTime = Double.parseDouble(String.format("%.8f", seconds / 86400.0)); // 1天 = 86400秒

        return new double[] { year % 100, startTime };
    }

    public static void main(String[] args) {
        String line = "CONTECSAT-1,59117,2023-04-15 12:30:45,6778.137,0.001234,51.6416,127.4238,85.6789,125.0";
        String[] parts = line.split(",");
        // String name = parts[0]; // 卫星名称
        String id = parts[1]; // NORAD ID
        String epoch = parts[2]; // 历元时间 (字符串)
        double[] time = parseTime(epoch);
        double a = Double.parseDouble(parts[3]); // 半长轴
        double nRadSec = Math.sqrt(MU / Math.pow(a, 3)) / (2 * Math.PI) * 86400;
        double eccentricity = Double.parseDouble(parts[4]); // 偏心率
        double inclination = Double.parseDouble(parts[5]); // 倾角
        double raan = Double.parseDouble(parts[6]); // 升交点赤经
        double argPerigee = Double.parseDouble(parts[7]); // 近地点幅角
        double trueAnomaly = Double.parseDouble(parts[8]); // 真近点角

        // int startYear = 17;
        // double startTime = 123.16666667;// 长度固定
        // double[] six = new double[6];
        // six[0] = 3;// 必须小于100
        // six[1] = 0.001;// 小于1
        // six[2] = 28.5;// 不能是负数
        // six[3] = 222;// 不超过360
        // six[4] = 111;// 不超过360
        // six[5] = 123;// 不超过360
        int startYear = (int) time[0];
        double startTime = time[1];// 长度固定
        double[] six = new double[6];
        six[0] = nRadSec;// 必须小于100
        six[1] = eccentricity;// 小于1
        six[2] = inclination;// 不能是负数
        six[3] = argPerigee;// 不超过360
        six[4] = raan;// 不超过360
        six[5] = 123;// 不超过360
        // String name = String.format("%.int", id);// 最多五位数
        String[] tle = new COE2TLEConversionAlg().simpleCoe2Tle(startYear, startTime, six, id);
        System.out.println(tle[0]);
        System.out.println(tle[1]);

    }
}