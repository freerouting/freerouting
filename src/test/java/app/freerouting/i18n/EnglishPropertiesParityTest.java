// Use this to test for missing keys in all .properties files
// For individual testing use:
// ./gradlew cleanTest test --tests app.freerouting.i18n.EnglishPropertiesParityTest --rerun-tasks
// Reports are in build/reports/i18n/*Report.txt and build/reports/i18n/*Report.json

package app.freerouting.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import app.freerouting.logger.FRLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnglishPropertiesParityTest {

  private static final Path JAVA_SOURCE_ROOT = Paths.get("src/main/java");
  private static final Path RESOURCE_ROOT = Paths.get("src/main/resources/app/freerouting");
  private static final Pattern TEXT_MANAGER_OWNER_PATTERN = Pattern.compile("new\\s+TextManager\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$.]*)\\.class");
  private static final Pattern TEXT_MANAGER_VARIABLE_PATTERN = Pattern.compile("(?:TextManager\\s+)?([A-Za-z_][A-Za-z0-9_\\.]*)\\s*=\\s*new\\s+TextManager\\s*\\(");
  private static final Pattern THIS_CLASS_PATTERN = Pattern.compile("new\\s+TextManager\\s*\\(\\s*(?:this\\.)?getClass\\s*\\(");
  private static final Pattern GET_TEXT_PATTERN = Pattern.compile("\\bgetText\\(\\s*\"([^\"]+)\"");
    private static final Pattern DYNAMIC_GET_TEXT_PATTERN = Pattern.compile(
      "\\bgetText\\(\\s*([A-Za-z_][A-Za-z0-9_$.\\[\\]\\s]*)\\.toString\\(\\)\\s*\\)");
    private static final Pattern FIELD_DECLARATION_PATTERN = Pattern.compile(
      "(?m)^\\s*(?:private|protected|public)?\\s*(?:static\\s+)?(?:final\\s+)?([A-Z][A-Za-z0-9_$.<>]*)\\s+([a-z_][A-Za-z0-9_]*)\\s*(?:[=;])");
      private static final Pattern ENUM_ARRAY_DECLARATION_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:final\\s+)?([A-Z][A-Za-z0-9_$.<>]*)\\[\\]\\s+([a-z_][A-Za-z0-9_]*)\\s*=\\s*[^;]*\\.values\\(\\)\\s*;");
  private static final Pattern SEGMENTED_BUTTONS_PATTERN = Pattern.compile("new\\s+SegmentedButtons\\s*\\((.*?)\\)", Pattern.DOTALL);
  private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"");
  private static final Map<String, String> BUNDLE_ALIASES = Map.of(
      "app.freerouting.gui.AirLine", "app.freerouting.interactive.RatsNest",
      "app.freerouting.drc.AirLine", "app.freerouting.interactive.RatsNest",
      "app.freerouting.rules.NetClasses", "app.freerouting.gui.WindowNetClasses");
  private static final Path REPORT_PATH_1 = Paths.get(
      "build/reports/i18n/CodeKeysExistInEnglishBundlesReport.txt");
  private static final Path REPORT_JSON_1 = Paths.get(
      "build/reports/i18n/CodeKeysExistInEnglishBundlesReport.json");
  private static final Path REPORT_PATH_2 = Paths.get(
      "build/reports/i18n/LocaleBundlesCoverEnglishBundlesReport.txt");
  private static final Path REPORT_JSON_2 = Paths.get(
      "build/reports/i18n/LocaleBundlesCoverEnglishBundlesReport.json");
  private static final Path REPORT_PATH_3 = Paths.get(
      "build/reports/i18n/EnglishBundlesContainKeysPresentInLocalesReport.txt");
  private static final Path REPORT_JSON_3 = Paths.get(
      "build/reports/i18n/EnglishBundlesContainKeysPresentInLocalesReport.json");
  private static Map<String, Path> sourceFilesCache;

  @Test
  @Order(1)
  void codeKeysExistInEnglishBundles() throws IOException {
    Map<String, Set<String>> sourceKeysByBundle = collectSourceKeysByBundle();
    Map<String, Set<String>> englishKeysByBundle = loadEnglishKeysByBundle();
    Set<String> commonEnglishKeys = loadPropertiesKeys(RESOURCE_ROOT.resolve("Common_en.properties"));

    List<String> missingReports = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : sourceKeysByBundle.entrySet()) {
      String bundle = entry.getKey();
      Set<String> usedKeys = entry.getValue();
      Set<String> availableKeys = new LinkedHashSet<>(commonEnglishKeys);
      Set<String> bundleEnglishKeys = englishKeysByBundle.get(bundle);
      if (bundleEnglishKeys != null) {
        availableKeys.addAll(bundleEnglishKeys);
      }

      Set<String> missingKeys = new TreeSet<>(usedKeys);
      missingKeys.removeAll(availableKeys);
      if (!missingKeys.isEmpty()) {
        missingReports.add(formatBundleSection(bundle, "missing from English bundles", missingKeys));
      }
    }

    writeReport("Source keys missing from English bundles", missingReports,
        REPORT_PATH_1, REPORT_JSON_1);
  }

  @Test
  @Order(2)
  void localeBundlesCoverEnglishBundles() throws IOException {
    Map<String, Set<String>> englishKeysByBundle = loadEnglishKeysByBundle();
    List<String> missingReports = new ArrayList<>();

    for (Map.Entry<String, Set<String>> entry : englishKeysByBundle.entrySet()) {
      String bundle = entry.getKey();
      Set<String> englishKeys = entry.getValue();
      Path englishFile = bundleToPropertiesPath(bundle, "en");
      Path parentDirectory = englishFile.getParent();
      String baseFileName = englishFile.getFileName().toString().substring(0,
          englishFile.getFileName().toString().length() - "_en.properties".length());

      Map<String, Set<String>> missingByLocaleFile = new TreeMap<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDirectory, baseFileName + "_*.properties")) {
        for (Path localeFile : stream) {
          if (localeFile.getFileName().toString().equals(englishFile.getFileName().toString())) {
            continue;
          }

          Set<String> localeKeys = loadPropertiesKeys(localeFile);
          Set<String> missingKeys = new TreeSet<>(englishKeys);
          missingKeys.removeAll(localeKeys);

          if (!missingKeys.isEmpty()) {
            missingByLocaleFile.put(localeFile.getFileName().toString(), missingKeys);
          }
        }
      }

      if (!missingByLocaleFile.isEmpty()) {
        missingReports.add(formatLocaleSection(bundle, "locale bundles missing English keys", missingByLocaleFile));
      }
    }

    writeReport("Locale bundles missing English keys", missingReports,
        REPORT_PATH_2, REPORT_JSON_2);
  }

  @Test
  @Order(3)
  void englishBundlesContainKeysPresentInLocales() throws IOException {
    Map<String, Set<String>> englishKeysByBundle = loadEnglishKeysByBundle();
    List<String> missingReports = new ArrayList<>();

    for (Map.Entry<String, Set<String>> entry : englishKeysByBundle.entrySet()) {
      String bundle = entry.getKey();
      Set<String> englishKeys = entry.getValue();
      Path englishFile = bundleToPropertiesPath(bundle, "en");
      Path parentDirectory = englishFile.getParent();
      String baseFileName = englishFile.getFileName().toString().substring(0,
          englishFile.getFileName().toString().length() - "_en.properties".length());

      Map<String, Set<String>> missingByKey = new TreeMap<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDirectory, baseFileName + "_*.properties")) {
        for (Path localeFile : stream) {
          if (localeFile.getFileName().toString().equals(englishFile.getFileName().toString())) {
            continue;
          }

          Set<String> localeKeys = loadPropertiesKeys(localeFile);
          Set<String> missingEnglishKeys = new TreeSet<>(localeKeys);
          missingEnglishKeys.removeAll(englishKeys);

          for (String missingKey : missingEnglishKeys) {
            missingByKey.computeIfAbsent(missingKey, ignored -> new TreeSet<>())
                .add(localeFile.getFileName().toString());
          }
        }
      }

      if (!missingByKey.isEmpty()) {
        missingReports.add(formatReverseLocaleSection(bundle,
            "keys present in locales but missing from English", missingByKey));
      }
    }

    writeReport("English bundles missing keys present in locales", missingReports,
        REPORT_PATH_3, REPORT_JSON_3);
  }

  private static Map<String, Set<String>> collectSourceKeysByBundle() throws IOException {
    Map<String, Set<String>> keysByBundle = new TreeMap<>();

    try (var paths = Files.walk(JAVA_SOURCE_ROOT)) {
      paths.filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !path.getFileName().toString().equals("TextManager.java"))
          .forEach(path -> {
            try {
              collectSourceKeysFromFile(path, keysByBundle);
            } catch (IOException e) {
              throw new IllegalStateException("Failed to scan source file: " + path, e);
            }
          });
    }

    return keysByBundle;
  }

  private static void collectSourceKeysFromFile(Path javaFile, Map<String, Set<String>> keysByBundle)
      throws IOException {
    String source = Files.readString(javaFile);
    Set<String> bundleOwners = resolveBundleOwners(javaFile, source);
    if (bundleOwners.isEmpty()) {
      return;
    }

    Set<String> textManagerVariables = resolveTextManagerVariables(source);
    Set<String> keys = new LinkedHashSet<>();

    for (String textManagerVariable : textManagerVariables) {
      Matcher getTextMatcher = Pattern.compile(
              "\\b(?:[A-Za-z_][A-Za-z0-9_]*\\.)*" + Pattern.quote(textManagerVariable) + "\\.getText\\(\\s*\"([^\"]+)\"")
          .matcher(source);
      while (getTextMatcher.find()) {
        String key = getTextMatcher.group(1);
        if (!isIconKey(key)) {
          keys.add(key);
        }
      }

      Matcher setTextMatcher = Pattern.compile(
              "\\b(?:[A-Za-z_][A-Za-z0-9_]*\\.)*" + Pattern.quote(textManagerVariable) + "\\.setText\\(\\s*[^,]+,\\s*\"([^\"]+)\"")
          .matcher(source);
      while (setTextMatcher.find()) {
        String key = setTextMatcher.group(1);
        if (!isIconKey(key)) {
          keys.add(key);
        }
      }
    }

    Matcher segmentedButtonsMatcher = SEGMENTED_BUTTONS_PATTERN.matcher(source);
    while (segmentedButtonsMatcher.find()) {
      String callSource = segmentedButtonsMatcher.group(1);
      List<String> quotedStrings = new ArrayList<>();
      Matcher quotedStringMatcher = QUOTED_STRING_PATTERN.matcher(callSource);
      while (quotedStringMatcher.find()) {
        quotedStrings.add(quotedStringMatcher.group(1));
      }
      if (quotedStrings.size() > 1) {
        keys.addAll(quotedStrings.subList(1, quotedStrings.size()));
      }
    }

    keys.addAll(resolveDynamicEnumKeys(source));

    for (String bundleOwner : bundleOwners) {
      keysByBundle.computeIfAbsent(bundleOwner, ignored -> new LinkedHashSet<>()).addAll(keys);
    }
  }

  private static Set<String> resolveBundleOwners(Path javaFile, String source) {
    Set<String> bundleOwners = new LinkedHashSet<>();
    String currentClassName = toClassName(javaFile);
    String currentPackageName = currentClassName.substring(0, currentClassName.lastIndexOf('.'));

    Matcher ownerMatcher = TEXT_MANAGER_OWNER_PATTERN.matcher(source);
    while (ownerMatcher.find()) {
      bundleOwners.add(resolveClassName(currentPackageName, ownerMatcher.group(1)));
    }

    if (THIS_CLASS_PATTERN.matcher(source).find()) {
      bundleOwners.add(currentClassName);
    }

    Set<String> resolvedOwners = new LinkedHashSet<>();
    for (String bundleOwner : bundleOwners) {
      resolvedOwners.add(BUNDLE_ALIASES.getOrDefault(bundleOwner, bundleOwner));
    }

    return resolvedOwners;
  }

  private static Set<String> resolveTextManagerVariables(String source) {
    Set<String> textManagerVariables = new LinkedHashSet<>();
    Matcher variableMatcher = TEXT_MANAGER_VARIABLE_PATTERN.matcher(source);
    while (variableMatcher.find()) {
      String textManagerVariable = variableMatcher.group(1);
      textManagerVariables.add(textManagerVariable);
      if (textManagerVariable.startsWith("this.")) {
        textManagerVariables.add(textManagerVariable.substring("this.".length()));
      }
    }

    return textManagerVariables;
  }

  private static Set<String> resolveDynamicEnumKeys(String source) throws IOException {
    Set<String> keys = new LinkedHashSet<>();
    Map<String, String> fieldTypes = resolveFieldTypes(source);
    Map<String, String> enumArrayTypes = resolveEnumArrayTypes(source);

    Matcher dynamicMatcher = DYNAMIC_GET_TEXT_PATTERN.matcher(source);
    while (dynamicMatcher.find()) {
      String expression = dynamicMatcher.group(1).trim();

      int arrayIndex = expression.indexOf('[');
      if (arrayIndex >= 0) {
        expression = expression.substring(0, arrayIndex).trim();
      }

      if (expression.contains(".values()")) {
        String enumType = expression.substring(0, expression.indexOf(".values()"));
        enumType = enumType.substring(enumType.lastIndexOf('.') + 1);
        keys.addAll(resolveEnumConstants(source, enumType));
        continue;
      }

      if (expression.startsWith("this.")) {
        expression = expression.substring("this.".length());
      }

      String enumType = fieldTypes.get(expression);
      if (enumType == null) {
        enumType = enumArrayTypes.get(expression);
      }
      if (enumType != null) {
        keys.addAll(resolveEnumConstants(source, enumType));
      }
    }

    return keys;
  }

  private static Map<String, String> resolveFieldTypes(String source) {
    Map<String, String> fieldTypes = new TreeMap<>();
    Matcher matcher = FIELD_DECLARATION_PATTERN.matcher(source);
    while (matcher.find()) {
      fieldTypes.put(matcher.group(2), matcher.group(1));
    }
    return fieldTypes;
  }

  private static Map<String, String> resolveEnumArrayTypes(String source) {
    Map<String, String> enumArrayTypes = new TreeMap<>();
    Matcher matcher = ENUM_ARRAY_DECLARATION_PATTERN.matcher(source);
    while (matcher.find()) {
      String enumType = matcher.group(1);
      if (enumType.contains(".")) {
        enumType = enumType.substring(enumType.lastIndexOf('.') + 1);
      }
      enumArrayTypes.put(matcher.group(2), enumType);
    }
    return enumArrayTypes;
  }

  private static Set<String> resolveEnumConstants(String source, String enumType) throws IOException {
    Set<String> constants = new LinkedHashSet<>();
    String enumBody = findEnumBody(source, enumType);

    if (enumBody == null) {
      Path enumSourceFile = findSourceFileBySimpleName(enumType + ".java");
      if (enumSourceFile != null) {
        String enumSource = Files.readString(enumSourceFile);
        enumBody = findEnumBody(enumSource, enumType);
      }
    }

    if (enumBody == null) {
      return constants;
    }

    int endOfConstants = enumBody.indexOf(';');
    String constantSection = endOfConstants >= 0 ? enumBody.substring(0, endOfConstants) : enumBody;
    constantSection = constantSection.replaceAll("//.*", "").replaceAll("(?s)/\\*.*?\\*/", "");
    for (String rawConstant : constantSection.split(",")) {
      String constant = rawConstant.trim();
      if (constant.isEmpty()) {
        continue;
      }
      int parenIndex = constant.indexOf('(');
      if (parenIndex >= 0) {
        constant = constant.substring(0, parenIndex).trim();
      }
      int braceIndex = constant.indexOf('{');
      if (braceIndex >= 0) {
        constant = constant.substring(0, braceIndex).trim();
      }
      if (constant.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        constants.add(constant);
      }
    }

    return constants;
  }

  private static String findEnumBody(String source, String enumType) {
    Pattern enumPattern = Pattern.compile(String.format("(?s)\\benum\\s+%s\\s*\\{", Pattern.quote(enumType)));
    Matcher matcher = enumPattern.matcher(source);
    if (matcher.find()) {
      int start = matcher.end();
      int depth = 1;
      for (int i = start; i < source.length(); i++) {
        char c = source.charAt(i);
        if (c == '{') {
          depth++;
        } else if (c == '}') {
          depth--;
          if (depth == 0) {
            return source.substring(start, i);
          }
        }
      }
    }
    return null;
  }

  private static Path findSourceFileBySimpleName(String fileName) throws IOException {
    synchronized (EnglishPropertiesParityTest.class) {
      if (sourceFilesCache == null) {
        sourceFilesCache = new java.util.HashMap<>();
        try (var paths = Files.walk(JAVA_SOURCE_ROOT)) {
          paths.filter(path -> path.toString().endsWith(".java"))
               .forEach(path -> sourceFilesCache.put(path.getFileName().toString(), path));
        }
      }
    }
    return sourceFilesCache.get(fileName);
  }

  private static boolean isIconKey(String key) {
    return key.startsWith("{{icon:") && key.endsWith("}}");
  }

  private static Map<String, Set<String>> loadEnglishKeysByBundle() throws IOException {
    Map<String, Set<String>> englishKeysByBundle = new TreeMap<>();

    try (var paths = Files.walk(RESOURCE_ROOT)) {
      paths.filter(path -> path.getFileName().toString().endsWith("_en.properties"))
          .forEach(path -> {
            try {
              englishKeysByBundle.put(toBundleName(path), loadPropertiesKeys(path));
            } catch (IOException e) {
              throw new IllegalStateException("Failed to load English bundle: " + path, e);
            }
          });
    }

    return englishKeysByBundle;
  }

  private static Set<String> loadPropertiesKeys(Path propertiesFile) throws IOException {
    Properties properties = new Properties();
    try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
      properties.load(inputStream);
    }
    return new LinkedHashSet<>(properties.stringPropertyNames());
  }

  private static String toClassName(Path javaFile) {
    Path relativePath = JAVA_SOURCE_ROOT.relativize(javaFile);
    String className = relativePath.toString().replace('\\', '.').replace('/', '.');
    return className.substring(0, className.length() - ".java".length());
  }

  private static String resolveClassName(String currentPackageName, String classToken) {
    if (classToken.contains(".")) {
      return classToken;
    }
    return currentPackageName + "." + classToken;
  }

  private static String toBundleName(Path propertiesFile) {
    String relativePath = RESOURCE_ROOT.relativize(propertiesFile).toString().replace('\\', '/');
    String withoutExtension = relativePath.substring(0, relativePath.length() - ".properties".length());
    if (withoutExtension.endsWith("_en")) {
      withoutExtension = withoutExtension.substring(0, withoutExtension.length() - "_en".length());
    }
    return "app.freerouting." + withoutExtension.replace('/', '.');
  }

  private static Path bundleToPropertiesPath(String bundle, String localeSuffix) {
    String relativePath = bundle.substring("app.freerouting.".length()).replace('.', '/');
    return RESOURCE_ROOT.resolve(relativePath + "_" + localeSuffix + ".properties");
  }

  private static String buildFailureMessage(String heading, List<String> reports) {
    StringBuilder builder = new StringBuilder(heading).append(System.lineSeparator());
    for (String report : reports) {
      builder.append("- ").append(report).append(System.lineSeparator());
    }
    return builder.toString();
  }

  private static String formatBundleSection(String bundle, String label, Set<String> keys) {
    StringBuilder builder = new StringBuilder();
    builder.append(bundle).append(System.lineSeparator());
    builder.append("  ").append(label).append(" (").append(keys.size()).append(")").append(System.lineSeparator());
    for (String key : keys) {
      builder.append("    - ").append(key).append(System.lineSeparator());
    }
    return builder.toString().trim();
  }

  private static String formatLocaleSection(String bundle, String label, Map<String, Set<String>> missingByLocaleFile) {
    StringBuilder builder = new StringBuilder();
    builder.append(bundle).append(System.lineSeparator());
    builder.append("  ").append(label).append(System.lineSeparator());
    for (Map.Entry<String, Set<String>> entry : missingByLocaleFile.entrySet()) {
      builder.append("    - ").append(entry.getKey()).append(" (").append(entry.getValue().size())
          .append("): ").append(String.join(", ", entry.getValue())).append(System.lineSeparator());
    }
    return builder.toString().trim();
  }

  private static String formatReverseLocaleSection(String bundle, String label, Map<String, Set<String>> missingByKey) {
    StringBuilder builder = new StringBuilder();
    builder.append(bundle).append(System.lineSeparator());
    builder.append("  ").append(label).append(System.lineSeparator());
    for (Map.Entry<String, Set<String>> entry : missingByKey.entrySet()) {
      builder.append("    - ").append(entry.getKey()).append(" (present in: ")
          .append(String.join(", ", entry.getValue())).append(")").append(System.lineSeparator());
    }
    return builder.toString().trim();
  }

  private static synchronized void writeReport(String heading, List<String> reports, Path reportPath, Path reportJsonPath)
      throws IOException {
    Files.createDirectories(reportPath.getParent());
    Files.deleteIfExists(reportPath);
    Files.deleteIfExists(reportJsonPath);

    // Write a human-friendly section
    StringBuilder builder = new StringBuilder();
    builder.append("== ").append(heading).append(" ==").append(System.lineSeparator());
    builder.append("count: ").append(reports.size()).append(System.lineSeparator());
    if (reports.isEmpty()) {
      builder.append("- none").append(System.lineSeparator());
    } else {
      for (String report : reports) {
        appendReportBlock(builder, report);
      }
    }
    builder.append(System.lineSeparator());

    Files.writeString(reportPath, builder.toString(), java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

    // Also write machine-readable JSON summary
    Map<String, Object> summary = new java.util.LinkedHashMap<>();
    summary.put("heading", heading);
    summary.put("count", reports.size());
    summary.put("items", reports);

    String jsonOut = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(summary);
    Files.writeString(reportJsonPath, jsonOut, java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

    FRLogger.info("Wrote i18n parity report to " + reportPath.toAbsolutePath());
    FRLogger.info("Wrote i18n parity JSON to " + reportJsonPath.toAbsolutePath());
  }

  private static void appendReportBlock(StringBuilder builder, String report) {
    String[] lines = report.split("\\R", -1);
    if (lines.length == 0) {
      return;
    }

    builder.append("- ").append(lines[0]).append(System.lineSeparator());
    for (int i = 1; i < lines.length; i++) {
      builder.append("  ").append(lines[i]).append(System.lineSeparator());
    }
  }
}