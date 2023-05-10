package com.roadl.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/*
https://github.com/javamelody/javamelody/blob/master/javamelody-core/src/main/java/net/bull/javamelody/internal/common/InputOutput.java#L35:20
 */
public final class InputOutput {
  private InputOutput() {
    super();
  }

  public static void pump(InputStream input, OutputStream output) throws IOException {
    final byte[] bytes = new byte[4 * 1024];
    int length = input.read(bytes);
    while (length != -1) {
      output.write(bytes, 0, length);
      length = input.read(bytes);
    }
  }

  public static byte[] pumpToByteArray(InputStream input) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    pump(input, out);
    return out.toByteArray();
  }

  public static String pumpToString(InputStream input, Charset charset) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    pump(input, out);
    return out.toString(charset.name());
  }

  public static void pumpToFile(InputStream input, File file) throws IOException {
    try (OutputStream output = new FileOutputStream(file)) {
      pump(input, output);
    }
  }

  public static void pumpFromFile(File file, OutputStream output) throws IOException {
    try (FileInputStream in = new FileInputStream(file)) {
      pump(in, output);
    }
  }

  public static void zipFile(File source, File target) throws IOException {
    final FileOutputStream fos = new FileOutputStream(target);
    try (ZipOutputStream zos = new ZipOutputStream(fos)) {
      final ZipEntry ze = new ZipEntry(source.getName());
      zos.putNextEntry(ze);
      pumpFromFile(source, zos);
      zos.closeEntry();
    }
  }

  public static boolean deleteFile(File file) {
    return file.delete();
  }

  public static void copyFile(File source, File target) throws IOException {
    try (FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target)) {
      pump(in, out);
    }
  }
}