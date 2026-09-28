/*
 * Autodesk Fusion Electronics - Freerouting Integration
 * Common Definitions, Constants, Path Helpers & Logging
 */

enum { false = 0, true = 1 };

string version = "2026-08-27";
real margin = 0.001; // margin for glueing wires

// Unit conversions
real U2G = 0.0;
string unit_str = "mil";
int unit = GRID_UNIT_MIL;
int Outline = 1;

// Global log configuration
string log_dir = "";
string log_file = "";
string status_message = "";

// Default file paths
string DSN_output_file = "";
string SCR_output_file = "";
int route_mode = 0; // 0 = Headless, 1 = GUI

// Helper: Normalize path to Windows backslashes
string to_win_path(string path) {
	string win = "";
	for (int i = 0; i < strlen(path); i++) {
		if (path[i] == '/') win += "\\";
		else win += strsub(path, i, 1);
	}
	return win;
}

// Helper: Normalize path to ULP forward slashes and clean duplicate slashes
string to_ulp_path(string path) {
	string ulp = "";
	for (int i = 0; i < strlen(path); i++) {
		if (path[i] == '\\') {
			if (strlen(ulp) == 0 || ulp[strlen(ulp) - 1] != '/') {
				ulp += "/";
			}
		} else if (path[i] == '/') {
			if (strlen(ulp) == 0 || ulp[strlen(ulp) - 1] != '/') {
				ulp += "/";
			}
		} else {
			ulp += strsub(path, i, 1);
		}
	}
	return ulp;
}

// Helper: Check if a file or directory exists
int file_exists(string path) {
	if (strlen(path) == 0) return 0;
	string files[];
	return fileglob(files, path) > 0;
}

/* Logging Support ********************************************************************/

void init_logging(void) {
	string user_dirs[];
	int n_users = fileglob(user_dirs, "C:/Users/*");
	log_dir = "";
	for (int i = 0; i < n_users; i++) {
		if (strstr(user_dirs[i], "Public") >= 0 || strstr(user_dirs[i], "Default") >= 0 || strstr(user_dirs[i], "All Users") >= 0) continue;
		string test_dir = user_dirs[i] + "/AppData/Local/freerouting";
		if (file_exists(test_dir)) {
			log_dir = to_ulp_path(test_dir + "/logs/fusion");
			break;
		}
	}
	if (strlen(log_dir) == 0) {
		for (int i = 0; i < n_users; i++) {
			if (strstr(user_dirs[i], "Public") < 0 && strstr(user_dirs[i], "Default") < 0 && strstr(user_dirs[i], "All Users") < 0) {
				log_dir = to_ulp_path(user_dirs[i] + "/AppData/Local/freerouting/logs/fusion");
				break;
			}
		}
	}
	if (strlen(log_dir) == 0) {
		log_dir = to_ulp_path(filedir(argv[0]) + "logs");
	}
	cfgset("Freerouting.LogDir", log_dir);

	if (!file_exists(log_dir)) {
		system("cmd /c if not exist \"" + to_win_path(log_dir) + "\" mkdir \"" + to_win_path(log_dir) + "\"");
	}
	log_file = log_dir + "/freerouting_fusion_plugin.log";
}

void log_msg(string level, string msg) {
	if (strlen(log_file) == 0) init_logging();
	string timestamp = t2string(time());
	string line;
	sprintf(line, "[%s] [%s] %s\n", timestamp, level, msg);
	output(log_file, "at") {
		printf("%s", line);
	};
}

void log_info(string msg) { log_msg("INFO", msg); }
void log_warn(string msg) { log_msg("WARN", msg); }
void log_error(string msg) { log_msg("ERROR", msg); }

void open_log_folder(void) {
	if (strlen(log_dir) == 0) init_logging();
	system("explorer \"" + to_win_path(log_dir) + "\"");
}

void ERROR(string msg) {
	log_error(msg);
	dlgMessageBox(":" + msg);
	exit(0);
	return;
}
