package com.iscas.satellite.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zip文本压缩、解压缩工具类
 */
public class ZIPUtil {

	/**
	 * 使用zip进行压缩
	 *
	 * @param str 压缩前的文本
	 * @return 返回压缩后的文本
	 */
	public static String zip(String str) {
		if (str == null)
			return null;
		byte[] compressed;
		ByteArrayOutputStream out = null;
		ZipOutputStream zout = null;
		String compressedStr = null;
		try {
			out = new ByteArrayOutputStream();
			zout = new ZipOutputStream(out);
			zout.putNextEntry(new ZipEntry("0"));
			zout.write(str.getBytes(StandardCharsets.UTF_8));
			zout.closeEntry();
		} catch (IOException e) {
			return compressedStr;
		} finally {
			if (zout != null) {
				try {
					zout.close();
					compressed = out.toByteArray();
					Encoder encoder = Base64.getEncoder();
					compressedStr = encoder.encodeToString(compressed);
				} catch (IOException ignored) {
				}
			}
			if (out != null) {
				try {
					out.close();
					compressed = out.toByteArray();
					Encoder encoder = Base64.getEncoder();
					compressedStr = encoder.encodeToString(compressed);
				} catch (IOException ignored) {
				}
			}
		}
		return compressedStr;
	}

	/**
	 * 使用zip进行解压缩
	 *
	 * @param compressedStr 压缩后的文本
	 * @return 解压后的字符串
	 */
	public static String unzip(String compressedStr) {
		if (compressedStr == null) {
			return null;
		}

		ByteArrayOutputStream out = null;
		ByteArrayInputStream in = null;
		ZipInputStream zin = null;
		String decompressed = null;
		try {
			Decoder decoder = Base64.getDecoder();
			byte[] compressed = decoder.decode(compressedStr);
			out = new ByteArrayOutputStream();
			in = new ByteArrayInputStream(compressed);
			zin = new ZipInputStream(in);
			zin.getNextEntry();
			byte[] buffer = new byte[1024];
			int offset = -1;
			while ((offset = zin.read(buffer)) != -1) {
				out.write(buffer, 0, offset);
			}
			decompressed = out.toString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			decompressed = null;
		} finally {
			if (zin != null) {
				try {
					zin.close();
				} catch (IOException ignored) {
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
				}
			}
		}
		return decompressed;
	}

	public static String compress(String str) throws IOException {
		if (str == null || str.isEmpty())
			return str;

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(str.getBytes(StandardCharsets.UTF_8));
		}
		return Base64.getEncoder().encodeToString(out.toByteArray());
	}

	public static void main(String[] args) throws IOException {
		String data = java.nio.file.Files.readString(java.nio.file.Paths.get("input/input3.txt"),
				StandardCharsets.UTF_8);
		// String data3 =
		// java.nio.file.Files.readString(java.nio.file.Paths.get("unzip/unzip.txt"),
		// StandardCharsets.UTF_8);
		String data3 = "[]";

		String zip = zip(data3);
		String compressZip = compress(data3);

		String unZip = unzip(zip);

		try (java.io.FileWriter writer = new java.io.FileWriter("zip/zip.txt")) {
			writer.write(zip);
			System.out.println("Unzipped content written to zip.txt");
		} catch (IOException e) {
			System.err.println("Error writing to file: " + e.getMessage());
		}

		try (java.io.FileWriter writer = new java.io.FileWriter("unzip/unzip.txt")) {
			writer.write(unZip);
			System.out.println("Unzipped content written to unzip.txt");
		} catch (IOException e) {
			System.err.println("Error writing to file: " + e.getMessage());
		}

		try (java.io.FileWriter writer = new java.io.FileWriter("zip/compress.txt")) {
			writer.write(compressZip);
			System.out.println("Unzipped content written to compress.txt");
		} catch (IOException e) {
			System.err.println("Error writing to file: " + e.getMessage());
		}
	}
}
