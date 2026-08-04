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
import java.util.HashMap;
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
import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnglishPropertiesParityTest {

  private static final Path JAVA_SOURCE_ROOT = Paths.get("src/main/java");
  private static final Path RESOURCE_ROOT = Paths.get("src/main/resources/app/freerouting");
  private static final Pattern TEXT_MANAGER_OWNER_PATTERN = Pattern.compile("new\\s+TextManager\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$.]*)\\.class");
  private static final Pattern TEXT_MANAGER_VARIABLE_PATTERN = Pattern.compile("(?:TextManager\\s+)?([A-Za-z_][A-Za-z0-9_\\.]*)\\s*=\\s*new\\s+TextManager\\s*\\(");
  private static final Pattern THIS_CLASS_PATTERN = Pattern.compile("new\\s+TextManager\\s*\\(\\s*(?:this\\.)?getClass\\s*\\(");
  private static final Pattern GET_TEXT_PATTERN = Pattern.compile("\\bgetText\\(\\s*\"([^\"]+)\"");
    private static final Pattern DYNAMIC_GET_TEXT_PATTERN = Pattern.compile(
      "\\bgetText\\(\\s*(.+?)\\.toString\\(\\)\\s*\\)");
    private static final Pattern FIELD_DECLARATION_PATTERN = Pattern.compile(
      "(?m)^\\s*(?:private|protected|public)?\\s*(?:static\\s+)?(?:final\\s+)?([A-Z][A-Za-z0-9_$.<>]*)\\s+([a-z_][A-Za-z0-9_]*)\\s*(?:[=;])");
      private static final Pattern ENUM_ARRAY_DECLARATION_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:final\\s+)?([A-Z][A-Za-z0-9_$.<>]*)\\[\\]\\s+([a-z_][A-Za-z0-9_]*)\\s*=\\s*[^;]*\\.values\\(\\)\\s*;");
  private static final Pattern SET_LANGUAGE_PATTERN = Pattern.compile("\\bsetLanguage\\s*\\(");
  private static final Pattern INHERITED_TM_USAGE_PATTERN = Pattern.compile("\\btm\\.(?:getText|setText)\\s*\\(");
  private static final Pattern EXTENDS_INTERACTIVE_STATE_PATTERN = Pattern.compile(
      "\\bextends\\s+(?:InteractiveState|\\w+State)\\b");
  private static final Pattern LOCAL_ENUM_ARRAY_PATTERN = Pattern.compile(
      "(?:final\\s+)?([A-Za-z_][A-Za-z0-9_.]*)\\[\\]\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\1\\.values\\(\\)\\s*;");
  private static final Pattern TEXT_MANAGER_SUFFIX_USAGE_PATTERN = Pattern.compile(
      "\\b([A-Za-z_][A-Za-z0-9_]*)tm\\.(?:getText|setText)\\s*\\(");
  private static final Pattern CREATE_WORD_WRAP_LABEL_PATTERN = Pattern.compile(
      "\\bcreateWordWrapLabel\\s*\\(\\s*\"([^\"]+)\"");
  private static final Pattern TEXT_MANAGER_DECL_PATTERN = Pattern.compile(
      "(?:TextManager\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*new\\s+TextManager\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$.]*)\\.class");
  private static final Pattern THIS_TM_ASSIGN_PATTERN = Pattern.compile(
      "this\\.tm\\s*=\\s*new\\s+TextManager\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$.]*)\\.class");
  private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);\\s*$", Pattern.MULTILINE);
  private static final Pattern STATIC_STRING_ARRAY_PATTERN = Pattern.compile(
      "private\\s+static\\s+final\\s+String\\[\\]\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\{([^}]+)\\}");
  private static final Pattern SEGMENTED_BUTTONS_PATTERN = Pattern.compile("new\\s+SegmentedButtons\\s*\\((.*?)\\)", Pattern.DOTALL);
  private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"");
  private static final Set<String> INTERACTIVE_STATE_BUNDLE_EXCEPTIONS = Set.of(
      "app.freerouting.interactive.InteractiveState",
      "app.freerouting.interactive.GuiBoardManager",
      "app.freerouting.interactive.ScreenMessages",
      "app.freerouting.interactive.RatsNest");
  private static final Map<String, String> BUNDLE_ALIASES = Map.ofEntries(
      Map.entry("app.freerouting.rules.NetClasses", "app.freerouting.gui.WindowNetClasses"),
      Map.entry("app.freerouting.boardgraphics.ItemColorTableModel", "app.freerouting.boardgraphics.ColorTableModel"),
      Map.entry("app.freerouting.boardgraphics.OtherColorTableModel", "app.freerouting.boardgraphics.ColorTableModel"),
      Map.entry("app.freerouting.interactive.AutorouterAndRouteOptimizerThread", "app.freerouting.interactive.InteractiveState"),
      Map.entry("app.freerouting.gui.AirLineInfo", "app.freerouting.drc.AirLine"),
      Map.entry("app.freerouting.gui.AirLine", "app.freerouting.drc.AirLine"),
      Map.entry("app.freerouting.gui.WindowRouteStubs", "app.freerouting.gui.CleanupWindows"),
      Map.entry("app.freerouting.gui.WindowUnconnectedRoute", "app.freerouting.gui.CleanupWindows"),
      Map.entry("app.freerouting.gui.WindowObjectListWithFilter", "app.freerouting.gui.WindowObjectList"),
      Map.entry("app.freerouting.gui.WindowIncompletes", "app.freerouting.gui.WindowObjectList"),
      Map.entry("app.freerouting.gui.WindowComponents", "app.freerouting.gui.WindowObjectList"),
      Map.entry("app.freerouting.gui.WindowPackages", "app.freerouting.gui.WindowObjectList"),
      Map.entry("app.freerouting.gui.WindowPadstacks", "app.freerouting.gui.WindowObjectList"));
  /** Subclasses with own bundles that override selected keys from {@link app.freerouting.gui.WindowObjectList}. */
  private static final Map<String, String> SUBCLASS_BUNDLE_PARENTS = Map.of(
      "app.freerouting.gui.WindowNets", "app.freerouting.gui.WindowObjectList",
      "app.freerouting.gui.WindowClearanceViolations", "app.freerouting.gui.WindowObjectList",
      "app.freerouting.gui.WindowLengthViolations", "app.freerouting.gui.WindowObjectList");
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
  private static final Path REPORT_PATH_4 = Paths.get(
      "build/reports/i18n/EnglishBundlesContainUnusedKeysReport.txt");
  private static final Path REPORT_JSON_4 = Paths.get(
      "build/reports/i18n/EnglishBundlesContainUnusedKeysReport.json");
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

  @Test
  @Order(4)
  void englishBundlesDoNotContainUnusedKeys() throws IOException {
    Map<String, Set<String>> sourceKeysByBundle = collectSourceKeysByBundle();
    Map<String, Set<String>> englishKeysByBundle = loadEnglishKeysByBundle();
    Set<String> commonEnglishKeys = loadPropertiesKeys(RESOURCE_ROOT.resolve("Common_en.properties"));

    Set<String> allUsedKeys = new LinkedHashSet<>();
    for (Set<String> keys : sourceKeysByBundle.values()) {
      allUsedKeys.addAll(keys);
    }
    expandWithImplicitCompanionKeys(allUsedKeys, commonEnglishKeys);
    for (Set<String> bundleEnglishKeys : englishKeysByBundle.values()) {
      expandWithImplicitCompanionKeys(allUsedKeys, bundleEnglishKeys);
    }

    List<String> unusedReports = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : englishKeysByBundle.entrySet()) {
      String bundle = entry.getKey();
      Set<String> englishKeys = entry.getValue();
      Set<String> usedKeys = bundle.endsWith(".Common")
          ? allUsedKeys
          : new LinkedHashSet<>(sourceKeysByBundle.getOrDefault(bundle, Set.of()));

      expandUsedKeysFromSubclassParentOverrides(bundle, englishKeys, sourceKeysByBundle, usedKeys);

      Set<String> availableKeys = new LinkedHashSet<>(englishKeys);
      expandWithImplicitCompanionKeys(usedKeys, availableKeys);

      Set<String> unusedKeys = new TreeSet<>(englishKeys);
      unusedKeys.removeAll(usedKeys);
      unusedKeys.removeIf(EnglishPropertiesParityTest::isIconKey);

      if (!unusedKeys.isEmpty()) {
        unusedReports.add(formatBundleSection(bundle, "unused in English bundle (not referenced in Java)", unusedKeys));
      }
    }

    writeReport("Unused keys in English bundles", unusedReports, REPORT_PATH_4, REPORT_JSON_4);

    if (!unusedReports.isEmpty()) {
      FRLogger.warn(
          "English bundles contain keys not referenced from Java source (count: "
              + unusedReports.size()
              + "). See "
              + REPORT_PATH_4
              + ". Review the report before running scripts/i18n/prune-unused-keys.py --apply.");
    }
  }

  /**
   * Keys derived at runtime from a base key (e.g. {@code save_tooltip} when {@code save} is passed to
   * {@link app.freerouting.util.TextManager#setText}).
   */
  private static void expandUsedKeysFromSubclassParentOverrides(
      String bundle,
      Set<String> englishKeys,
      Map<String, Set<String>> sourceKeysByBundle,
      Set<String> usedKeys) {
    String parentBundle = SUBCLASS_BUNDLE_PARENTS.get(bundle);
    if (parentBundle == null) {
      return;
    }
    Set<String> parentUsedKeys = sourceKeysByBundle.getOrDefault(parentBundle, Set.of());
    for (String key : parentUsedKeys) {
      if (englishKeys.contains(key)) {
        usedKeys.add(key);
      }
    }
  }

  private static void expandWithImplicitCompanionKeys(Set<String> usedKeys, Set<String> availableKeys) {
    Set<String> companions = new LinkedHashSet<>();
    for (String key : usedKeys) {
      addCompanionIfPresent(companions, availableKeys, key + "_tooltip");
      addCompanionIfPresent(companions, availableKeys, key + "_hover_info");
    }
    usedKeys.addAll(companions);
  }

  private static void addCompanionIfPresent(Set<String> companions, Set<String> availableKeys, String candidate) {
    if (availableKeys.contains(candidate)) {
      companions.add(candidate);
    }
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
    String currentClassName = toClassName(javaFile);
    String currentPackageName = currentClassName.substring(0, currentClassName.lastIndexOf('.'));
    Map<String, String> imports = resolveImports(source);
    Map<String, String> textManagerBundles = resolveTextManagerBundles(source, currentClassName, currentPackageName, imports);

    if (textManagerBundles.isEmpty()) {
      Set<String> bundleOwners = resolveBundleOwners(javaFile, source, imports);
      if (bundleOwners.isEmpty()) {
        return;
      }
      Set<String> keys = collectFileKeys(source);
      for (String bundleOwner : bundleOwners) {
        addKeysToBundle(keysByBundle, bundleOwner, keys);
      }
      return;
    }

    if (textManagerBundles.size() == 1) {
      Set<String> keys = collectFileKeys(source);
      addKeysToBundle(keysByBundle, textManagerBundles.values().iterator().next(), keys);
      return;
    }

    for (Map.Entry<String, String> entry : textManagerBundles.entrySet()) {
      Set<String> variableKeys = new LinkedHashSet<>();
      collectKeysForTextManagerVariable(source, entry.getKey(), variableKeys);
      addKeysToBundle(keysByBundle, entry.getValue(), variableKeys);
    }

    Set<String> sharedKeys = collectSharedFileKeys(source);
    String primaryBundle = textManagerBundles.getOrDefault("tm", textManagerBundles.values().iterator().next());
    addKeysToBundle(keysByBundle, primaryBundle, sharedKeys);
  }

  private static Set<String> collectFileKeys(String source) throws IOException {
    Set<String> keys = new LinkedHashSet<>();
    Set<String> textManagerVariables = resolveTextManagerVariables(source);
    for (String textManagerVariable : textManagerVariables) {
      collectKeysForTextManagerVariable(source, textManagerVariable, keys);
    }
    keys.addAll(collectSharedFileKeys(source));
    return keys;
  }

  private static Set<String> collectSharedFileKeys(String source) throws IOException {
    Set<String> keys = new LinkedHashSet<>();

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
    keys.addAll(resolveStaticStringArrayKeys(source));
    keys.addAll(resolveCreateWordWrapLabelKeys(source));

    Matcher textManagerSuffixMatcher = TEXT_MANAGER_SUFFIX_USAGE_PATTERN.matcher(source);
    while (textManagerSuffixMatcher.find()) {
      String textManagerVariable = textManagerSuffixMatcher.group(1) + "tm";
      collectKeysForTextManagerVariable(source, textManagerVariable, keys);
    }

    return keys;
  }

  private static void addKeysToBundle(Map<String, Set<String>> keysByBundle, String bundle, Set<String> keys) {
    if (keys.isEmpty()) {
      return;
    }
    keysByBundle.computeIfAbsent(canonicalizeBundle(bundle), ignored -> new LinkedHashSet<>()).addAll(keys);
  }

  private static Map<String, String> resolveTextManagerBundles(
      String source, String currentClassName, String currentPackageName, Map<String, String> imports) {
    Map<String, String> variableToBundle = new TreeMap<>();

    if (SET_LANGUAGE_PATTERN.matcher(source).find() || THIS_CLASS_PATTERN.matcher(source).find()) {
      variableToBundle.put("tm", currentClassName);
    }

    Matcher declMatcher = TEXT_MANAGER_DECL_PATTERN.matcher(source);
    while (declMatcher.find()) {
      variableToBundle.put(
          declMatcher.group(1),
          resolveClassName(currentPackageName, declMatcher.group(2), imports));
    }

    Matcher thisTmMatcher = THIS_TM_ASSIGN_PATTERN.matcher(source);
    while (thisTmMatcher.find()) {
      variableToBundle.put("tm", resolveClassName(currentPackageName, thisTmMatcher.group(1), imports));
    }

    if (source.contains("protected final TextManager tm")
        && EXTENDS_INTERACTIVE_STATE_PATTERN.matcher(source).find()) {
      variableToBundle.put("tm", "app.freerouting.interactive.InteractiveState");
    }

    Map<String, String> canonicalized = new TreeMap<>();
    for (Map.Entry<String, String> entry : variableToBundle.entrySet()) {
      canonicalized.put(entry.getKey(), canonicalizeBundle(entry.getValue()));
    }
    return canonicalized;
  }

  private static Map<String, String> resolveImports(String source) {
    Map<String, String> imports = new HashMap<>();
    Matcher matcher = IMPORT_PATTERN.matcher(source);
    while (matcher.find()) {
      String fqn = matcher.group(1);
      imports.put(fqn.substring(fqn.lastIndexOf('.') + 1), fqn);
    }
    return imports;
  }

  private static String canonicalizeBundle(String bundle) {
    String resolved = BUNDLE_ALIASES.getOrDefault(bundle, bundle);
    if (resolved.startsWith("app.freerouting.interactive.")
        && resolved.endsWith("State")
        && !INTERACTIVE_STATE_BUNDLE_EXCEPTIONS.contains(resolved)) {
      return "app.freerouting.interactive.InteractiveState";
    }
    return resolved;
  }

  private static Set<String> resolveBundleOwners(Path javaFile, String source, Map<String, String> imports) {
    Set<String> bundleOwners = new LinkedHashSet<>();
    String currentClassName = toClassName(javaFile);
    String currentPackageName = currentClassName.substring(0, currentClassName.lastIndexOf('.'));

    Matcher ownerMatcher = TEXT_MANAGER_OWNER_PATTERN.matcher(source);
    while (ownerMatcher.find()) {
      bundleOwners.add(resolveClassName(currentPackageName, ownerMatcher.group(1), imports));
    }

    if (THIS_CLASS_PATTERN.matcher(source).find()) {
      bundleOwners.add(currentClassName);
    }

    if (SET_LANGUAGE_PATTERN.matcher(source).find() && INHERITED_TM_USAGE_PATTERN.matcher(source).find()) {
      bundleOwners.add(currentClassName);
    }

    if (INHERITED_TM_USAGE_PATTERN.matcher(source).find()) {
      bundleOwners.add(currentClassName);
    }

    if (source.contains("extends WindowObjectList")) {
      bundleOwners.add("app.freerouting.gui.WindowObjectList");
    }

    if (EXTENDS_INTERACTIVE_STATE_PATTERN.matcher(source).find()
        && INHERITED_TM_USAGE_PATTERN.matcher(source).find()
        && currentClassName.startsWith("app.freerouting.interactive.")
        && !currentClassName.equals("app.freerouting.interactive.InteractiveState")) {
      bundleOwners.add("app.freerouting.interactive.InteractiveState");
    }

    Set<String> resolvedOwners = new LinkedHashSet<>();
    for (String bundleOwner : bundleOwners) {
      resolvedOwners.add(canonicalizeBundle(bundleOwner));
    }

    return resolvedOwners;
  }

  private static Set<String> resolveBundleOwners(Path javaFile, String source) throws IOException {
    return resolveBundleOwners(javaFile, source, resolveImports(source));
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

    if (INHERITED_TM_USAGE_PATTERN.matcher(source).find()) {
      textManagerVariables.add("tm");
    }

    return textManagerVariables;
  }

  private static void collectKeysForTextManagerVariable(String source, String textManagerVariable, Set<String> keys) {
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

    Matcher inlineGetTextMatcher = Pattern.compile(
            "(?:setText|setToolTipText)\\s*\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.)*"
                + Pattern.quote(textManagerVariable) + "\\.getText\\(\\s*\"([^\"]+)\"")
        .matcher(source);
    while (inlineGetTextMatcher.find()) {
      String key = inlineGetTextMatcher.group(1);
      if (!isIconKey(key)) {
        keys.add(key);
      }
    }
  }

  private static Set<String> resolveCreateWordWrapLabelKeys(String source) {
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = CREATE_WORD_WRAP_LABEL_PATTERN.matcher(source);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }

  private static Set<String> resolveStaticStringArrayKeys(String source) {
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = STATIC_STRING_ARRAY_PATTERN.matcher(source);
    while (matcher.find()) {
      if ("reserved_name_chars".equals(matcher.group(1))) {
        continue;
      }
      Matcher quotedMatcher = QUOTED_STRING_PATTERN.matcher(matcher.group(2));
      while (quotedMatcher.find()) {
        keys.add(quotedMatcher.group(1));
      }
    }
    return keys;
  }

  private static Map<String, String> resolveLocalEnumArrayTypes(String source) {
    Map<String, String> localEnumArrayTypes = new TreeMap<>();
    Matcher matcher = LOCAL_ENUM_ARRAY_PATTERN.matcher(source);
    while (matcher.find()) {
      String enumType = matcher.group(1);
      if (enumType.contains(".")) {
        enumType = enumType.substring(enumType.lastIndexOf('.') + 1);
      }
      localEnumArrayTypes.put(matcher.group(2), enumType);
    }
    return localEnumArrayTypes;
  }

  private static Set<String> resolveDynamicEnumKeys(String source) throws IOException {
    Set<String> keys = new LinkedHashSet<>();
    Map<String, String> fieldTypes = resolveFieldTypes(source);
    Map<String, String> enumArrayTypes = resolveEnumArrayTypes(source);
    enumArrayTypes.putAll(resolveLocalEnumArrayTypes(source));

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
      enumBody = findEnumBodyInAnySourceFile(enumType);
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

  private static String findEnumBodyInAnySourceFile(String enumType) throws IOException {
    synchronized (EnglishPropertiesParityTest.class) {
      if (sourceFilesCache == null) {
        sourceFilesCache = new java.util.HashMap<>();
        try (var paths = Files.walk(JAVA_SOURCE_ROOT)) {
          paths.filter(path -> path.toString().endsWith(".java"))
               .forEach(path -> sourceFilesCache.put(path.getFileName().toString(), path));
        }
      }
    }

    Pattern enumPattern = Pattern.compile(String.format("(?s)\\benum\\s+%s\\s*\\{", Pattern.quote(enumType)));
    for (Path sourceFile : sourceFilesCache.values()) {
      String enumSource = Files.readString(sourceFile);
      if (!enumPattern.matcher(enumSource).find()) {
        continue;
      }
      String enumBody = findEnumBody(enumSource, enumType);
      if (enumBody != null) {
        return enumBody;
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
    return resolveClassName(currentPackageName, classToken, Map.of());
  }

  private static String resolveClassName(String currentPackageName, String classToken, Map<String, String> imports) {
    if (classToken.contains(".")) {
      return classToken;
    }
    if (imports.containsKey(classToken)) {
      return imports.get(classToken);
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