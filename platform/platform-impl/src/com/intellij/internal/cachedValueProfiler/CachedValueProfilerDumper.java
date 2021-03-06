// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.google.gson.stream.JsonWriter;
import com.intellij.psi.util.CachedValueProfiler;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static java.time.temporal.ChronoField.*;

final class CachedValueProfilerDumper {
  private CachedValueProfilerDumper() {
  }

  @NotNull
  public static File dumpResults(@Nullable File dir) throws IOException {
    List<TotalInfo> infos = prepareInfo();

    String fileName = String.format("dump-%s.cvp", time());
    File file = new File(dir, fileName);
    try (JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8)))) {
      writeJson(infos, writer);
    }
    return file;
  }

  @NotNull
  private static List<TotalInfo> prepareInfo() {
    MultiMap<StackTraceElement, CachedValueProfiler.Info> snapshot = CachedValueProfiler.getInstance().getStorageSnapshot();
    List<TotalInfo> list = new ArrayList<>();
    snapshot.entrySet().forEach((entry) -> list.add(new TotalInfo(entry.getKey(), entry.getValue())));

    list.sort(Comparator.comparing(info -> ((double)info.used) / info.infos.size()));
    return list;
  }

  private static final class TotalInfo {
    final String origin;
    final long lifetime;
    final long used;
    final long cost;

    final List<CachedValueProfiler.Info> infos;

    TotalInfo(@NotNull StackTraceElement origin, @NotNull Collection<CachedValueProfiler.Info> infos) {
      this.origin = origin.toString();
      this.infos = List.copyOf(infos);

      lifetime = this.infos.stream().mapToLong(value -> value.getLifetime()).sum();
      used = this.infos.stream().mapToLong(value -> value.getUseCount()).sum();
      cost = this.infos.stream().mapToLong(value -> value.getComputeTime()).sum();
    }
  }

  @NotNull
  private static String time() {
    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .optionalStart().appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2)
      .appendLiteral('|')
      .appendValue(YEAR)
      .appendLiteral('-')
      .appendValue(MONTH_OF_YEAR, 2)
      .appendLiteral('-')
      .appendValue(DAY_OF_MONTH, 2)
      .toFormatter();
    return LocalDateTime.now().format(formatter);
  }

  private static void writeJson(List<TotalInfo> infos, JsonWriter writer) throws IOException {
    writer.setIndent("  ");
    writer.beginArray();
    for (TotalInfo info : infos) {
      writer.beginObject();
      writer.name("origin").value(info.origin);
      writer.name("count").value(info.infos.size());
      writer.name("cost").value(info.cost);
      writer.name("used").value(info.used);
      writer.name("lifetime").value(info.lifetime);
      writer.endObject();
    }
    writer.endArray();
  }
}
