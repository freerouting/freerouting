/*
 * Autodesk Fusion Electronics - Freerouting Integration
 * Environment Detection (Java, JAR) & Freerouting Execution
 */

string java_path = "";
int java_status = 0; // 0 = not found, 1 = ok

string jar_path = "";
int jar_status = 0; // 0 = not found, 1 = ok

/* Environment Detection ***************************************************************/

void detect_java(void) {
	java_path = cfgget("Freerouting.JavaPath", "");
	if (strlen(java_path) > 0 && file_exists(java_path)) {
		java_status = 1;
		return;
	}

	string user_dirs[];
	int n_users = fileglob(user_dirs, "C:/Users/*");
	for (int i = 0; i < n_users; i++) {
		string jres[];
		int n = fileglob(jres, user_dirs[i] + "/AppData/Local/freerouting/cache/jre/*/bin/java.exe");
		if (n > 0) {
			java_path = to_ulp_path(jres[0]);
			java_status = 1;
			cfgset("Freerouting.JavaPath", java_path);
			return;
		}
	}

	string candidates[];
	// Check Eclipse Adoptium / Temurin
	int n = fileglob(candidates, "C:/Program Files/Eclipse Adoptium/*/bin/java.exe");
	if (n > 0) {
		java_path = to_ulp_path(candidates[0]);
		java_status = 1;
		cfgset("Freerouting.JavaPath", java_path);
		return;
	}

	// Check standard Java JDKs / JREs
	n = fileglob(candidates, "C:/Program Files/Java/*/bin/java.exe");
	if (n > 0) {
		java_path = to_ulp_path(candidates[0]);
		java_status = 1;
		cfgset("Freerouting.JavaPath", java_path);
		return;
	}

	// Check Microsoft OpenJDK
	n = fileglob(candidates, "C:/Program Files/Microsoft/*/bin/java.exe");
	if (n > 0) {
		java_path = to_ulp_path(candidates[0]);
		java_status = 1;
		cfgset("Freerouting.JavaPath", java_path);
		return;
	}

	java_path = "java";
	java_status = 1;
}

void detect_jar(void) {
	jar_path = cfgget("Freerouting.JarPath", "");
	if (strlen(jar_path) > 0 && file_exists(jar_path)) {
		jar_status = 1;
		return;
	}

	// 1. Build directory
	if (file_exists("C:/Work/freerouting/build/libs/freerouting-current-executable.jar")) {
		jar_path = "C:/Work/freerouting/build/libs/freerouting-current-executable.jar";
		jar_status = 1;
		cfgset("Freerouting.JarPath", jar_path);
		return;
	}

	string user_dirs[];
	int n_users = fileglob(user_dirs, "C:/Users/*");
	for (int i = 0; i < n_users; i++) {
		string candidates[];
		int n = fileglob(candidates, user_dirs[i] + "/AppData/Local/freerouting/*.jar");
		if (n > 0) {
			jar_path = to_ulp_path(candidates[0]);
			jar_status = 1;
			cfgset("Freerouting.JarPath", jar_path);
			return;
		}

		n = fileglob(candidates, user_dirs[i] + "/Downloads/freerouting*.jar");
		if (n > 0) {
			jar_path = to_ulp_path(candidates[0]);
			jar_status = 1;
			cfgset("Freerouting.JarPath", jar_path);
			return;
		}
	}

	// Same directory as ULP
	string local_jars[];
	int n = fileglob(local_jars, filedir(argv[0]) + "*.jar");
	if (n > 0) {
		jar_path = to_ulp_path(local_jars[0]);
		jar_status = 1;
		cfgset("Freerouting.JarPath", jar_path);
		return;
	}
}

/* Freerouting Process Execution ******************************************************/

int do_run_freerouting(int is_headless) {
	if (!file_exists(DSN_output_file)) {
		status_message = "DSN file does not exist. Export DSN first.";
		log_error(status_message);
		return 0;
	}
	if (jar_status != 1 || strlen(jar_path) == 0) {
		status_message = "Freerouting JAR not located. Please locate it in Step 2.";
		log_error(status_message);
		return 0;
	}

	string win_java = to_win_path(java_path);
	string win_jar = to_win_path(jar_path);
	string win_dsn = to_win_path(DSN_output_file);
	string win_scr = to_win_path(SCR_output_file);
	string router_log = to_win_path(log_dir + "/freerouting.log");

	string cmd;
	if (is_headless) {
		log_info("Running Freerouting in headless mode (synchronous)...");
		status_message = "Running Freerouting auto-router in background...";
		sprintf(cmd, "\"%s\" -jar \"%s\" -de \"%s\" -do \"%s\" -l \"%s\" --gui.enabled=false --api_server.enabled=false --mcp_server.enabled=false",
			win_java, win_jar, win_dsn, win_scr, router_log);
	} else {
		log_info("Launching Freerouting GUI...");
		status_message = "Freerouting GUI launched. Save script when done.";
		string win_javaw = win_java;
		int jpos = strstr(win_javaw, "java.exe");
		if (jpos >= 0) {
			win_javaw = strsub(win_javaw, 0, jpos) + "javaw.exe";
		}
		sprintf(cmd, "cmd.exe /c start \"\" \"%s\" -jar \"%s\" -de \"%s\" -do \"%s\" -l \"%s\" --api_server.enabled=false --mcp_server.enabled=false",
			win_javaw, win_jar, win_dsn, win_scr, router_log);
	}

	log_info("Executing command: " + cmd);
	int ret = system(cmd);
	string ret_str;
	sprintf(ret_str, "%d", ret);
	log_info("Execution finished with code: " + ret_str);

	if (is_headless) {
		if (file_exists(SCR_output_file)) {
			status_message = "Auto-routing complete. Script generated at: " + SCR_output_file;
			log_info(status_message);
			return 1;
		} else {
			status_message = "Auto-routing completed without generating script. Check log file.";
			log_warn(status_message);
			return 0;
		}
	}
	return 1;
}
