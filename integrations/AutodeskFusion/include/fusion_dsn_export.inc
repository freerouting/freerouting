/*
 * Autodesk Fusion Electronics - Freerouting Integration
 * Specctra DSN File Generation & Geometry Export
 */

// Indentation constants
string T1 = "  ", T2 = "    ", T3 = "      ", T4 = "        ";

// Geometry and shape storage
int Vnum = 0, Pnum = 0, Snum = 0, Lnum = 0, default_via_nbr = 0;
string V_name[], V_list[], P_name[], P_list[], S_name[], S_list[];
string L_names[]; int B_layers[];
int p[], x1[], y1[], x2[], y2[], width[], rad[], arcx1[], arcy1[], xc[], yc[]; real a1[], a2[], linkl;
string dr_name[], dr_value[], layer_def = "";
real default_wire_width, default_clearance, clearance_wire_pad, clearance_wire_via, clearance_wire_smd;
real clearance_pad_pad, clearance_pad_via, clearance_via_via, clearance_smd_pad, clearance_smd_via, clearance_smd_smd, dim_clearance;
real default_drill_size, default_via_size, min_pad_t, rv_pad_t, max_pad_t, min_pad_i, rv_pad_i, max_pad_i, min_pad_b, rv_pad_b, max_pad_b, min_via_inner, min_via_outer;
int u_layer_start = 100, u_layer_end = 120;
string wire_protect = "", poly_protect = "", poly_drop = "", via_protect = "";

string num2str(real val) {
	string s;
	sprintf(s, "%.4f", val);
	int len = strlen(s);
	if (strstr(s, ".") >= 0) {
		while (len > 0 && s[len - 1] == '0') {
			s = strsub(s, 0, len - 1);
			len--;
		}
		if (len > 0 && s[len - 1] == '.') {
			s = strsub(s, 0, len - 1);
		}
	}
	if (strlen(s) == 0 || s == "-0") s = "0";
	return s;
}

string via_sig(int shape, int dia, int drill, int start, int end) {
	string s, t; int count = 1;
	sprintf(t, "%d:%d:%d:%d:%d", shape, dia, drill, start, end);
	for (int i = 1; i <= Vnum; i++) {
		s = V_list[i]; if (strtol(s) == shape) count++;
		if (s == t) return V_name[i];
	}
	Vnum++; V_list[Vnum] = t;
	switch(shape) {
		case VIA_SHAPE_SQUARE:  s = "Square"; break;
		case VIA_SHAPE_ROUND:   s = "Round"; break;
		case VIA_SHAPE_OCTAGON: s = "Octagon"; break;
		default:                s = "Round"; break;
	}
	sprintf(V_name[Vnum], "\"%s%d$%s\"", s, count, num2str(U2G * drill));
	return V_name[Vnum];
}

string pad_sig(int shape, int dia, int drill, int elong, real angl) {
	string s, t; int count = 0;
	sprintf(t, "%d:%d:%d:%d:%f", shape, dia, drill, elong, angl);
	for (int i = 1; i <= Pnum; i++) {
		s = P_list[i]; if (strtol(s) == shape) count++;
		if (t == s) return P_name[i];
	}
	Pnum++; P_list[Pnum] = t; count++;
	switch(shape) {
		case PAD_SHAPE_SQUARE:	s = "Square"; break;
		case PAD_SHAPE_ROUND:	s = "Round"; break;
		case PAD_SHAPE_OCTAGON: s = "Octagon"; break;
		case PAD_SHAPE_LONG: 	s = "Oblong"; break;
		case PAD_SHAPE_OFFSET:	s = "Offset"; break;
		default:                s = "Round"; break;
	}
	sprintf(P_name[Pnum], "\"%s%d\"", s, count);
	return P_name[Pnum];
}

string smd_sig(int rns, int x, int y, int layer, real angl) {
	string s, t; int count = 0;
	sprintf(t, "%d:%d:%d:%d:%f", rns, x, y, layer, angl);
	for (int i = 1; i <= Snum; i++) {
		s = S_list[i]; count++;
		if (t == s) return S_name[i];
	}
	Snum++; S_list[Snum] = t; count++;
	sprintf(S_name[Snum], "\"SMD_%d\"", count);
	return S_name[Snum];
}

string W_Circle(string lname, real dia) {
	string t;
	sprintf(t, T3 + "(shape (circle %s %s 0 0))\n", lname, num2str(dia));
	return t;
}

string W_Quart(real x, real y, real rad, real rot) {
	string s, t; if (rad == 0) return t;
	real lx, ly, angle; rot *= PI/180;
	for (int i = 1; i <= 16; i++) {
		angle = PI * i / 32;
		lx = x * cos(rot) + rad * cos(angle + rot);
		ly = y * sin(rot) + rad * sin(angle + rot);
		sprintf(s, " %s %s", num2str(lx), num2str(ly)); t += s;
	}
	return t;
}

string W_Octagon(string lname, real dia, real rotate) {
	string s, t;
	real step = PI/4, rad = dia * 0.5411961, angle, dx, dy;
	sprintf(t, T3 + "(shape (polygon %s 0 ", lname);
	for (int i = 0; i < 8; i++) {
		angle = step * i + PI/8 + rotate * PI/180;
		dx = rad * cos(angle); dy = rad * sin(angle);
		sprintf(s, " %s %s", num2str(dx), num2str(dy)); t += s;
	}
	return t + "))\n";
}

string W_SMDpad(string lname, real dx, real dy, real rot, real rdness) {
	string s, t; int k, adj[];
	if (min(dx, dy) <= 0) return "";
	if (rdness == 100 && dx <= dy) { adj[0] = -1; adj[2] = -1; }
	if (rdness == 100 && dy <= dx) { adj[1] = -1; adj[3] = -1; }
	real agl, crad = min(dx, dy) * rdness / 200;
	real cx = dx/2 - crad, cy = dy/2 - crad;
	if (!cx) agl = PI/2; else agl = atan(cy/cx);
	real brad = sqrt(cx * cx + cy * cy);
	real ax[] = {agl, PI - agl, PI + agl, -agl};
	rot *= PI/180;
	sprintf(t, T3 + "(shape (polygon %s 0", lname);
	for (k = 0; k <= 3; k++) {
		real tx = brad * cos(ax[k] + rot), ty = brad * sin(ax[k] + rot);
		if (rdness == 0) { sprintf(s, " %s %s", num2str(tx), num2str(ty)); t += s; }
		else {
			for (int j = 0; j <= 5 + adj[k]; j++) {
				real angle = j * PI/10 + k * PI/2 + rot;
				real rx = crad * cos(angle), ry = crad * sin(angle);
				sprintf(s, " %s %s", num2str(tx + rx), num2str(ty + ry)); t += s;
			}
		}
	}
	return t + "))\n";
}

string W_Rect(string lname, real x, real y, real rotate) {
	string s, t;
	if (rotate == 0 || rotate == 180.0) {
		sprintf(t, T3 + "(shape (rect %s %s %s %s %s))\n", lname, num2str(-x/2), num2str(-y/2), num2str(x/2), num2str(y/2));
		return t;
	}
	real step = PI/4, rad = sqrt(x * x + y * y)/2, angle[], dx, dy;
	angle[0] = atan(y/x); angle[4] = angle[0];
	angle[1] = PI - angle[0]; angle[2] = PI + angle[0]; angle[3] = 2 * PI - angle[0];
	rotate *= PI/180;
	sprintf(t, T3 + "(shape (polygon %s 0 ", lname);
	for (int i = 0; i < 5; i++) {
		dx = rad * cos(angle[i] + rotate); dy = rad * sin(angle[i] + rotate);
		sprintf(s, "%s %s ", num2str(dx), num2str(dy)); t += s;
	}
	return t + "))\n";
}

string W_Pshape(string lname, real dia, real elong, char offset, real r) {
	string t;
	real x = dia * elong / 100, sx = -x/2, ex = x/2;
	if (offset) { sx = 0; ex = x; }
	r *= PI/180;
	if (r) {
		sprintf(t, T3 + "(shape (path %s %s %s %s %s %s (aperture_type round)))\n",
			lname, num2str(dia), num2str(sx * cos(r)), num2str(sx * sin(r)), num2str(ex * cos(r)), num2str(ex * sin(r)));
		return t;
	}
	sprintf(t, T3 + "(shape (path %s %s %s 0 %s 0 (aperture_type round)))\n",
		lname, num2str(dia), num2str(sx), num2str(ex));
	return t;
}

real xmin, xmax, ymin, ymax;
char b_status = false;

void get_boundary(char state) {
	if (b_status && state) return;
	if (state) { xmin = ymin = REAL_MAX; xmax = ymax = -REAL_MAX; }
	b_status = state;
	return;
}

void calc_boundary(real x, real y) {
	if (!b_status) return;
	xmin = min(x, xmin); xmax = max(x, xmax);
	ymin = min(y, ymin); ymax = max(y, ymax);
	return;
}

int glue(int x, int y) {
	int seg = 0; real d1, d2;
	for (int i = 1; i <= p[0]; i++) {
		if (p[i] == 0) {
			d1 = sqrt(pow(x1[i] - x, 2) + pow(y1[i] - y, 2));
			d2 = sqrt(pow(x2[i] - x, 2) + pow(y2[i] - y, 2));
			if ((d1 <= margin) && (d1 < linkl)) { linkl = d1; seg = i; }
			if ((d2 <= margin) && (d2 < linkl)) { linkl = d2; seg = -i; }
		}
	}
	return seg;
}

int new_seg(void) {
	int seg = 0;
	linkl = REAL_MAX;
	seg = glue(x2[0], y2[0]);
	return seg;
}

void swap(int seg) {
	int x, y;
	x = x1[seg]; y = y1[seg];
	x1[seg] = x2[seg]; y1[seg] = y2[seg];
	x2[seg] = x; y2[seg] = y;
	if (rad[seg]) {
		a1[seg] = a1[seg] + PI;
		a2[seg] = a2[seg] + PI;
		if (a1[seg] > 2 * PI) a1[seg] -= 2 * PI;
		if (a2[seg] > 2 * PI) a2[seg] -= 2 * PI;
	}
	return;
}

string write_arc2line(int j) {
	string s, t; real angle, step, r = U2G * rad[j];
	if (r < 0.001) return "";
	real a_start = a1[j], a_end = a2[j];
	if (a_start > a_end) a_end += 2 * PI;
	step = (a_end - a_start) / 16;
	for (int i = 1; i <= 16; i++) {
		angle = a_start + i * step;
		real ax = U2G * xc[j] + r * cos(angle);
		real ay = U2G * yc[j] + r * sin(angle);
		calc_boundary(ax, ay);
		sprintf(s, "%s %s ", num2str(ax), num2str(ay)); t += s;
	}
	return t;
}

void load_one_wire(UL_WIRE W) {
	p[0] = 0;
	int i = 1;
	p[i] = 0;
	x1[i] = W.x1; y1[i] = W.y1; x2[i] = W.x2; y2[i] = W.y2; width[i] = W.width; rad[i] = 0;
	if (W.arc) {
		rad[i] = W.arc.radius; xc[i] = W.arc.xc; yc[i] = W.arc.yc; arcx1[i] = W.arc.x1; arcy1[i] = W.arc.y1;
		a1[i] = W.arc.angle1 * PI / 180; a2[i] = W.arc.angle2 * PI / 180;
	}
	p[0] = i; p[i] = -1;
	return;
}

void load_layer_wires(UL_WIRE W, int L) {
	int i = p[0];
	if (W.layer == L) {
		i++; p[i] = 0;
		x1[i] = W.x1; y1[i] = W.y1; x2[i] = W.x2; y2[i] = W.y2; width[i] = W.width; rad[i] = 0;
		if (W.arc) {
			rad[i] = W.arc.radius; xc[i] = W.arc.xc; yc[i] = W.arc.yc; arcx1[i] = W.arc.x1; arcy1[i] = W.arc.y1;
			a1[i] = W.arc.angle1 * PI / 180; a2[i] = W.arc.angle2 * PI / 180;
		}
		p[0] = i;
	}
	return;
}

void process_wires(void) {
	for (int i = 1; i <= p[0]; i++) {
		int from, j;
		if (!p[i]) { p[i] = -i; from = i; x2[0] = x2[from]; y2[0] = y2[from]; }
		for (j = new_seg(); j;) {
			if (j < 0) { j = abs(j); swap(j); }
			if (p[from] < 0) p[from] = -j; else p[from] = j;
			from = p[j] = j; x2[0] = x2[j]; y2[0] = y2[j];
			j = new_seg();
		}
	}
	return;
}

string write_wire_shape(string pre, string post, char add_size) {
	string s, t;
	int i, j, go;
	real xloc, yloc;
	for (i = 1; i <= p[0]; i++) {
		if (p[i] < 0) {
			j = i; go = true; p[j] = -p[j];
			real pts_x[], pts_y[];
			string pts_str[];
			int pt_count = 0;

			while (go) {
				xloc = U2G * x1[j]; yloc = U2G * y1[j]; calc_boundary(xloc, yloc);
				pts_x[pt_count] = xloc;
				pts_y[pt_count] = yloc;
				sprintf(pts_str[pt_count], "%s %s", num2str(xloc), num2str(yloc));
				if (rad[j]) {
					pts_str[pt_count] += write_arc2line(j);
				}
				pt_count++;
				if (p[j] == j || j > p[0]) go = false; else j = p[j];
			}
			xloc = U2G * x2[j]; yloc = U2G * y2[j];
			pts_x[pt_count] = xloc;
			pts_y[pt_count] = yloc;
			sprintf(pts_str[pt_count], "%s %s", num2str(xloc), num2str(yloc));
			pt_count++;

			// Check polygon orientation if closed (first point == last point)
			real area = 0.0;
			for (int k = 0; k < pt_count - 1; k++) {
				area += (pts_x[k] * pts_y[k + 1] - pts_x[k + 1] * pts_y[k]);
			}
			int is_closed = (abs(pts_x[0] - pts_x[pt_count - 1]) < 0.1 && abs(pts_y[0] - pts_y[pt_count - 1]) < 0.1);
			if (is_closed && area < 0) {
				// Reverse vertex order to guarantee Counter-Clockwise (CCW) bounded shape
				t += pre;
				if (add_size) { sprintf(s, " %s ", num2str(U2G * width[i])); t += s; }
				for (int k = pt_count - 1; k >= 0; k--) {
					t += pts_str[k] + " ";
				}
				t += post;
			} else {
				t += pre;
				if (add_size) { sprintf(s, " %s ", num2str(U2G * width[i])); t += s; }
				for (int k = 0; k < pt_count; k++) {
					t += pts_str[k] + " ";
				}
				t += post;
			}
		}
	}
	p[0] = 0;
	return t;
}

void get_designrules(void) {
	if (!board) ERROR("No board found. Run this ULP in an Autodesk Fusion PCB Layout window!");
	board(B) {
		U2G = u2mic(1); // Standardized to micrometers (um) matching KiCad DSN resolution

		string brd_data, dr_lines[], par_list[]; int d_rule_count = 0;
		if (strlen(B.name) > 0 && fileread(brd_data, B.name)) {
			if (xmlelements(dr_lines, brd_data, "eagle/drawing/board/designrules") > 0) {
				d_rule_count = xmlelements(par_list, dr_lines[0], "designrules/param");
				for (int i = 0; i < d_rule_count; i++) {
					dr_name[i] = xmlattribute(par_list[i], "param", "name");
					dr_value[i] = xmlattribute(par_list[i], "param", "value");
				}
			}
		}

		if (strlen(layer_def) == 0) {
			int sig_layers[], num_sig = 0;
			sig_layers[num_sig++] = LAYER_TOP; // Always layer 1 (Top)

			B.layers(L) {
				if (L.used && (L.number > 1) && (L.number < 16)) {
					sig_layers[num_sig++] = L.number;
				}
			}

			// Include Bottom layer (16) for standard 2+ layer boards
			int has_bottom = 0;
			B.layers(L) {
				if (L.number == LAYER_BOTTOM) {
					has_bottom = 1;
				}
			}
			if (has_bottom) {
				sig_layers[num_sig++] = LAYER_BOTTOM;
			}

			if (num_sig == 1) {
				layer_def = "(1)";
			} else {
				layer_def = "(";
				for (int i = 0; i < num_sig; i++) {
					string l_str;
					sprintf(l_str, "%d", sig_layers[i]);
					layer_def += l_str;
					if (i < num_sig - 1) layer_def += "*";
				}
				layer_def += ")";
			}
		}
	}
}

real par_value(string param) {
	string scale; int i = 0;
	while (dr_name[i]) {
		if (param == dr_name[i]) {
			if (param == "layerSetup") { layer_def = dr_value[i]; return 1; }
			scale = strupr(strsub(dr_value[i], strlen(dr_value[i]) - 2));
			if (scale == "MM") return U2G * mm2u(strtod(dr_value[i]));
			if (scale == "IL") return U2G * mil2u(strtod(dr_value[i]));
			if (scale == "CH") return U2G * inch2u(strtod(dr_value[i]));
			if (scale == "IC") return U2G * mic2u(strtod(dr_value[i]));
			return strtod(dr_value[i]);
		}
		i++;
	}
	if (param == "layerSetup") {
		if (strlen(layer_def) == 0) layer_def = "(1*16)";
		return 1;
	}
	if (param == "msWidth") return U2G * mm2u(0.25);
	if (param == "mdCopperDimension") return U2G * mm2u(0.5);
	if (param == "msDrill") return U2G * mm2u(0.35);
	if (param == "rlMinViaInner" || param == "rlMinViaOuter") return U2G * mm2u(0.2);
	if (param == "rlMinPadTop" || param == "rlMinPadInner" || param == "rlMinPadBottom") return U2G * mm2u(0.25);
	if (param == "rlMaxPadTop" || param == "rlMaxPadInner" || param == "rlMaxPadBottom") return U2G * mm2u(0.5);
	if (param == "rvPadTop" || param == "rvPadInner" || param == "rvPadBottom") return 0.25;
	return U2G * mm2u(0.2);
}

int layer_structure(string L) {
	char TBV = false, BBV = false;
	int i = 0, j = 1, k, tbv[], bbv[], sp = 0, N, NLast, stack[];
	int vsize = default_via_size / U2G, dsize = default_drill_size / U2G;
	do {
		if (isdigit(L[i])) {
			if (isdigit(L[i+1])) { N = strtol(" " + L[i+1]) + 10; i += 2; }
			else { N = strtol(" " + L[i]); i++; }
			if (TBV) { tbv[j] = N; TBV = false; i++; }
			else if (BBV) { bbv[j-1] = N; BBV = false; i++; }
			else { B_layers[j] = N; NLast = N; j++; for (k = 0; k <= i; k++) if (stack[k] < 0) stack[k] = N; }
		}
		else switch (L[i]) {
			case '[': TBV = true; i++; break;
			case ':': BBV = true; i++; break;
			case '(': stack[sp] = -1; sp++; i++; break;
			case ')': sp--; i++;
				via_sig(VIA_SHAPE_ROUND, vsize, dsize, stack[sp], NLast); break;
			default: i++;
		}
	} while (i <= strlen(L));

	for (i = 1; i < j; i++) {
		if (tbv[i]) via_sig(VIA_SHAPE_ROUND, vsize, dsize, B_layers[i], tbv[i]);
		if (bbv[i]) via_sig(VIA_SHAPE_ROUND, vsize, dsize, bbv[i], B_layers[i]);
	}
	default_via_nbr = Vnum;
	board(B) {
		for (i = 1; i < j; i++) {
			if (B_layers[i] == LAYER_TOP) {
				L_names[i] = "Top";
			} else if (B_layers[i] == LAYER_BOTTOM) {
				L_names[i] = "Bottom";
			} else {
				B.layers(L) {
					if (L.number == B_layers[i]) L_names[i] = L.name;
				}
			}
		}
	}
	return j - 1;
}

void read_board_pads_and_vias(void) {
	board(B) {
		B.libraries(L) {
			L.packages(P) {
				P.contacts(C) {
					if (C.pad) {
						pad_sig(C.pad.shape[LAYER_TOP], C.pad.diameter[LAYER_TOP],
							C.pad.drill, C.pad.elongation, C.pad.angle);
					}
					if (C.smd) {
						smd_sig(C.smd.roundness, C.smd.dx, C.smd.dy, C.smd.layer, C.smd.angle);
					}
				}
			}
		}
		B.signals(N) {
			N.vias(V) {
				via_sig(V.shape[LAYER_VIAS], V.diameter[LAYER_TOP], V.drill, V.start, V.end);
			}
		}
	}
	return;
}

string LN2name(int nmbr) {
	if (nmbr == LAYER_TOP && Lnum >= 1) return L_names[1];
	if (nmbr == LAYER_BOTTOM && Lnum >= 1) return L_names[Lnum];
	for (int i = 1; i <= Lnum; i++) {
		if (B_layers[i] == nmbr) return L_names[i];
	}
	board(B) {
		B.layers(L) {
			if (L.number == nmbr) return L.name;
		}
	}
	return "Top";
}

/* SPECCTRA descriptor generators ******************************************************/

string Parser_d(void) {
	string s, t, bname;
	bname = filename(DSN_output_file);
	if (strlen(bname) == 0) {
		board(B) { bname = B.name; };
	}
	t = "(pcb " + bname + "\n";
	t += "  (parser\n";
	t += "    (string_quote \")\n";
	t += "    (space_in_quoted_tokens on)\n";
	t += "    (host_cad \"Autodesk Fusion\")\n";
	sprintf(s, "    (host_version \"%d.%d\")\n", EAGLE_VERSION, EAGLE_RELEASE);
	t += s;
	t += "    (case_sensitive off)\n    (via_rotate_first on)\n  )\n";
	return t;
}

string Resolution_d(void) {
	return "  (resolution um 10)\n  (unit um)\n";
}

string Layer_d(void) {
	string s, t;
	board(B) {
		for (int i = 1; i <= Lnum; i++) {
			int is_power = 0;
			if (B_layers[i] > 1 && B_layers[i] < 16) {
				B.signals(N) {
					N.polyPours(P) {
						if (P.layer == B_layers[i]) is_power = 1;
					}
				}
				B.layers(L) {
					if (L.number == B_layers[i] && (strstr(L.name, "$") >= 0 || strstr(L.name, "GND") >= 0 || strstr(L.name, "VCC") >= 0)) {
						is_power = 1;
					}
				}
			}
			string type_str = is_power ? "power" : "signal";
			sprintf(s, T2 + "(layer %s\n" + T3 + "(type %s)\n" + T3 + "(property\n" + T4 + "(index %d)\n" + T3 + ")\n" + T2 + ")\n",
				L_names[i], type_str, i - 1);
			t += s;
		}
	}
	return t;
}

string Boundary_d(void) {
	string s, t, ct;
	board(B) {
		B.circles(C) {
			if (C.layer == LAYER_DIMENSION) {
				get_boundary(true);
				real rad = C.radius * U2G, cx = C.x * U2G, cy = C.y * U2G;
				calc_boundary(cx - rad, cy - rad); calc_boundary(cx + rad, cy + rad);
				ct += T2 + "(boundary\n      (path pcb 0 ";
				for (int i = 0; i < 256; i++) {
					sprintf(s, "%s %s ", num2str(cx + cos(i * PI / 128) * rad), num2str(cy + sin(i * PI / 128) * rad)); ct += s;
				}
				ct += ")\n" + T3 + "(clearance_class boundary)\n    )\n";
			}
		}
		B.wires(W) {
			if (W.layer == LAYER_DIMENSION) {
				load_layer_wires(W, W.layer);
			}
		}
	}
	process_wires();
	if (p[0]) get_boundary(true);
	s = write_wire_shape("    (boundary\n      (path pcb 0 ", ")\n      (clearance_class boundary)\n    )\n", false);
	get_boundary(false);
	if (xmax - xmin == 0 || ymax - ymin == 0) ERROR("No PCB outline defined!");
	if (strlen(s) == 0 && strlen(ct) == 0) {
		sprintf(t, "    (boundary\n      (rect pcb %s %s %s %s))\n", num2str(xmin), num2str(ymin), num2str(xmax), num2str(ymax));
		return t;
	}
	return ct + s;
}

string Plane_d(void) {
	string s, t;
	string processed_planes = ",";
	board(B) {
		B.signals(N) {
			N.polyPours(P) {
				if (P.layer > 1 && P.layer < 16) {
					string key = "";
					sprintf(key, ",%s#%d,", N.name, P.layer);
					if (strstr(processed_planes, key) < 0) {
						processed_planes += key;
						sprintf(s, T2 + "(plane \"%s\"\n" + T3 + "(polygon %s 0 %s %s %s %s %s %s %s %s %s %s))\n",
							N.name, LN2name(P.layer),
							num2str(xmin), num2str(ymin), num2str(xmax), num2str(ymin), num2str(xmax), num2str(ymax), num2str(xmin), num2str(ymax), num2str(xmin), num2str(ymin));
						t += s;
					}
				}
			}
		}
	}
	return t;
}

string Keepout_d(void) {
	string s, Lname, KOname, t;
	string TopLayer = LN2name(LAYER_TOP), BotLayer = LN2name(LAYER_BOTTOM);
	board(B) {
		B.layers(L) {
			char in_range = true;
			switch (L.number) {
				case LAYER_TKEEPOUT:  Lname = TopLayer; KOname = "place_keepout"; break;
				case LAYER_BKEEPOUT:  Lname = BotLayer; KOname = "place_keepout"; break;
				case LAYER_TRESTRICT: Lname = TopLayer; KOname = "wire_keepout"; break;
				case LAYER_BRESTRICT: Lname = BotLayer; KOname = "wire_keepout"; break;
				case LAYER_VRESTRICT: Lname = "signal"; KOname = "via_keepout"; break;
				default: in_range = false;
			}
			if (L.number >= u_layer_start && L.number <= u_layer_end && L.used) {
				KOname = ""; string num[]; strsplit(num, L.name, '=');
				Lname = LN2name(strtol(num[1]));
				if (strstr(L.name, "wire_restrict_layer") >= 0) KOname = "wire_keepout";
				if (strstr(L.name, "via_restrict_layer") >= 0) KOname = "via_keepout";
				if (KOname && Lname != "signal") in_range = true;
			}
			if (in_range) {
				B.wires(W) {
					if (W.layer == L.number) load_layer_wires(W, L.number);
				}
				process_wires();
				string pre = T2 + "(" + KOname + "(polygon " + Lname + " 0 ";
				t += write_wire_shape(pre, "))\n", false);
				B.circles(C) {
					if (C.layer == L.number) {
						sprintf(s, T2 + "(%s(circ %s %s %s %s))\n", KOname, Lname, num2str(U2G * C.radius * 2), num2str(U2G * C.x), num2str(U2G * C.y));
						t += s;
					}
				}
				B.rectangles(R) {
					if (R.layer == L.number) {
						sprintf(s, T2 + "(%s(rect %s %s %s %s %s))\n", KOname, Lname, num2str(U2G * R.x1), num2str(U2G * R.y1), num2str(U2G * R.x2), num2str(U2G * R.y2));
						t += s;
					}
				}
				B.polyShapes(PO) {
					if (PO.layer == L.number) {
						sprintf(s, T2 + "(%s(polygon %s 0", KOname, Lname); t += s;
						PO.contours(W) {
							sprintf(s, " %s %s", num2str(U2G * W.x1), num2str(U2G * W.y1)); t += s;
						}
						t += "))\n";
					}
				}
			}
		}
		B.holes(H) {
			sprintf(s, "    (keepout (circ signal %s %s %s))\n", num2str(U2G * H.drill + 2 * dim_clearance), num2str(U2G * H.x), num2str(U2G * H.y));
			t += s;
		}
	}
	return t;
}

string Via_d(void) {
	string t = "    (via\n";
	if (!Vnum || Lnum == 1) {
		via_sig(VIA_SHAPE_ROUND, default_via_size / U2G, default_drill_size / U2G, 0, 0);
		sprintf(V_name[Vnum], "\"ViaDefault$%s\"", num2str(default_drill_size));
	}
	for (int i = 1; i <= Vnum; i++) t += T3 + V_name[i] + "\n";
	t += T2 + ")\n";
	t += T2 + "(control\n" + T3 + "(via_at_smd on)\n";
	return t + T2 + ")\n";
}

string print_cl(real cl_val, string cl_type) {
	string t;
	if (cl_val > 0 && cl_val != default_clearance) sprintf(t, "    (rule (clearance %s (type %s)))\n", num2str(cl_val), cl_type);
	return t;
}

string Rule_d(void) {
	string s, t;
	sprintf(t, "    (rule (width %s)(clearance %s))\n", num2str(default_wire_width), num2str(default_clearance));
	sprintf(s, "    (rule (clearance %s (type default_boundary)))\n", num2str(dim_clearance));
	t += s;
	t += print_cl(clearance_wire_pad, "wire_pin");
	t += print_cl(clearance_wire_smd, "wire_smd");
	t += print_cl(clearance_wire_via, "wire_via");
	t += print_cl(clearance_pad_pad, "pin_pin");
	t += print_cl(clearance_pad_via, "pin_via");
	t += print_cl(clearance_via_via, "via_via");
	t += print_cl(clearance_smd_pad, "smd_pin");
	t += print_cl(clearance_smd_via, "smd_via");
	t += print_cl(clearance_smd_smd, "smd_smd");
	t += print_cl(dim_clearance, "area_wire");
	t += print_cl(dim_clearance, "area_via");
	return t;
}

string Placement_d(void) {
	string t = "  (placement\n    (place_control (flip_style rotate_first))\n";
	string s;
	board(B) {
		B.elements(C) {
			string pkg_id = "\"" + C.package.name + "$" + C.package.library + "\"";
			string side = C.mirror ? "Back" : "Front";
			sprintf(s, "    (component %s\n      (place \"%s\" %s %s %s %s)\n    )\n",
				pkg_id, C.name, num2str(C.x * U2G), num2str(C.y * U2G), side, num2str(C.angle));
			t += s;
		}
	}
	return t + "  )\n";
}

string single_image_d(UL_PACKAGE P) {
	string s, t, Lname, KOname;
	sprintf(t, T2 + "(image \"%s$%s\"\n", P.name, P.library);
	board(B) {
		B.layers(L) {
			char in_range = true;
			switch (L.number) {
				case LAYER_TKEEPOUT:  Lname = LN2name(LAYER_TOP); KOname = "place_keepout"; break;
				case LAYER_BKEEPOUT:  Lname = LN2name(LAYER_BOTTOM); KOname = "place_keepout"; break;
				case LAYER_TRESTRICT: Lname = LN2name(LAYER_TOP); KOname = "wire_keepout"; break;
				case LAYER_BRESTRICT: Lname = LN2name(LAYER_BOTTOM); KOname = "wire_keepout"; break;
				case LAYER_VRESTRICT: Lname = "signal"; KOname = "via_keepout"; break;
				case LAYER_TPLACE:    Lname = "signal"; KOname = "outline"; break;
				default: in_range = false;
			}
			if (in_range) {
				if (KOname == "outline") {
					string pre = T3 + "(outline (path signal 0 ";
					P.wires(W) {
						if (Outline == 1) {
							load_layer_wires(W, L.number);
							process_wires();
							t += write_wire_shape(pre, "))\n", false);
						}
						if (Outline == 2) {
							load_one_wire(W);
							t += write_wire_shape(pre, "))\n", false);
						}
					}
				} else {
					string pre = T3 + "(" + KOname + "(polygon " + Lname + " 0 ";
					P.wires(W) {
						load_layer_wires(W, L.number);
						process_wires();
						t += write_wire_shape(pre, "))\n", false);
					}
				}
				P.circles(C) {
					if (C.layer == L.number) {
						sprintf(s, T3 + "(%s(circ %s %s %s %s))\n", KOname, Lname, num2str(U2G * C.radius * 2), num2str(U2G * C.x), num2str(U2G * C.y));
						t += s;
					}
				}
				P.rectangles(R) {
					if (R.layer == L.number) {
						sprintf(s, T3 + "(%s(rect %s %s %s %s %s))\n", KOname, Lname, num2str(U2G * R.x1), num2str(U2G * R.y1), num2str(U2G * R.x2), num2str(U2G * R.y2));
						t += s;
					}
				}
				P.polyShapes(PO) {
					if (PO.layer == L.number) {
						sprintf(s, T2 + "(%s(polygon %s 0", KOname, Lname); t += s;
						PO.contours(W) {
							sprintf(s, " %s %s", num2str(U2G * W.x1), num2str(U2G * W.y1)); t += s;
						}
						t += "))\n";
					}
				}
			}
		}
		P.holes(H) {
			sprintf(s, T3 + "(keepout (circ signal %s %s %s))\n", num2str(U2G * H.drill + 2 * dim_clearance), num2str(U2G * H.x), num2str(U2G * H.y));
			t += s + T3 + "(clearance_class boundary)\n";
		}
		P.contacts(C) {
			string pname;
			if (C.pad) {
				int act_pad_shape = max(C.pad.shape[LAYER_TOP], C.pad.shape[LAYER_BOTTOM]);
				int act_pad_dia = max(C.pad.diameter[LAYER_TOP], C.pad.diameter[LAYER_BOTTOM]);
				pname = pad_sig(act_pad_shape, act_pad_dia, C.pad.drill, C.pad.elongation, C.pad.angle);
				sprintf(s, T3 + "(pin %s \"%s\" %s %s)\n", pname, C.name, num2str(U2G * C.x), num2str(U2G * C.y));
				t += s;
			}
			if (C.smd) {
				pname = smd_sig(C.smd.roundness, C.smd.dx, C.smd.dy, C.smd.layer, C.smd.angle);
				sprintf(s, T3 + "(pin %s \"%s\" %s %s)\n", pname, C.name, num2str(U2G * C.x), num2str(U2G * C.y));
				t += s;
			}
		}
	}
	return t += T2 + ")\n";
}

string Images_d(void) {
	string t;
	board(B) {
		B.libraries(Lib) {
			Lib.packages(P) {
				t += single_image_d(P);
			}
		}
	}
	return t;
}

string Padstack_d(void) {
	string s, t, pmem[], vmem[], last_line = T3 + "(attach off)\n" + T2 + ")\n";
	real dia, x, y, drill, elong, rotate; int i, j, offset;

	sprintf(s, T2 + "(padstack \"ViaDefault$%s\"\n", num2str(default_drill_size)); t += s;
	t += W_Circle("signal", default_via_size) + last_line;

	for (i = 1; i <= Vnum; i++) {
		strsplit(vmem, V_list[i], ':');
		int start = strtol(vmem[3]), end = strtol(vmem[4]);
		if (start + end == 0) continue;
		sprintf(s, T2 + "(padstack %s\n", V_name[i]); t += s;
		dia = strtol(vmem[1]) * U2G; real inner_dia = strtol(vmem[2]) * U2G + 2 * min_via_inner;
		for (j = 1; j <= Lnum; j++) {
			if (start > B_layers[j] || end < B_layers[j]) continue;
			int L = B_layers[j]; char outer_layer = (L == B_layers[1] || L == B_layers[Lnum]);
			if (outer_layer) {
				if (strstr(V_name[i], "Round") >= 0)   t += W_Circle(L_names[j], dia);
				if (strstr(V_name[i], "Square") >= 0)  t += W_Rect(L_names[j], dia, dia, 0);
				if (strstr(V_name[i], "Octagon") >= 0) t += W_Octagon(L_names[j], dia, 0);
			}
			else t += W_Circle(L_names[j], inner_dia);
		}
		t += last_line;
	}

	i = 1;
	while (i <= Snum) {
		strsplit(pmem, S_list[i], ':');
		x = strtol(pmem[1]) * U2G; y = strtol(pmem[2]) * U2G; rotate = strtod(pmem[4]);
		string lname = LN2name(strtol(pmem[3])); int rdness = strtol(pmem[0]);
		sprintf(s, T2 + "(padstack %s\n", S_name[i]); t += s;
		t += W_SMDpad(lname, x, y, rotate, rdness);
		t += last_line; i++;
	}

	i = 1;
	while (i <= Pnum) {
		strsplit(pmem, P_list[i], ':'); offset = false;
		dia = strtol(pmem[1]) * U2G; rotate = strtod(pmem[4]); elong = strtod(pmem[3]);
		drill = strtol(pmem[2]) * U2G;
		j = 2; string inner_layers = "";
		while (Lnum > 2 && j < Lnum) {
			inner_layers += W_Circle(L_names[j], dia);
			j++;
		}
		sprintf(s, T2 + "(padstack %s\n", P_name[i]); t += s;
		switch(strtol(pmem[0])) {
			case PAD_SHAPE_SQUARE:
				t += W_Rect(L_names[1], dia, dia, rotate) + inner_layers;
				if (Lnum > 1) t += W_Rect(L_names[Lnum], dia, dia, rotate);
				break;
			case PAD_SHAPE_ROUND:
				t += W_Circle(L_names[1], dia) + inner_layers;
				if (Lnum > 1) t += W_Circle(L_names[Lnum], dia);
				break;
			case PAD_SHAPE_OCTAGON:
				t += W_Octagon(L_names[1], dia, rotate) + inner_layers;
				if (Lnum > 1) t += W_Octagon(L_names[Lnum], dia, rotate);
				break;
			case PAD_SHAPE_OFFSET:
				offset = true;
			case PAD_SHAPE_LONG:
				t += W_Pshape(L_names[1], dia, elong, offset, rotate);
				t += inner_layers;
				if (Lnum > 1) t += W_Pshape(L_names[Lnum], dia, elong, offset, rotate);
				break;
			default:
				t += W_Circle(L_names[1], dia) + inner_layers;
				if (Lnum > 1) t += W_Circle(L_names[Lnum], dia);
				break;
		}
		t += last_line; i++;
	}
	return t;
}

string Network_d(void) {
	int i, j = 0, net_class_number[];
	string s, t = T1 + "(network\n", net_names[];
	board(B) {
		B.signals(S) {
			sprintf(s, T2 + "(net \"%s\"\n" + T3 + "(pins", S.name); t += s;
			S.contactrefs(R) {
				sprintf(s, " \"%s\"-\"%s\"", R.element.name, R.contact.name); t += s;
			}
			t += ")\n" + T2 + ")\n";
			net_names[j] = S.name;
			net_class_number[j] = S.class.number;
			++j;
		}
		B.classes(CL) {
			if ((CL.width == 0) && (CL.clearance == 0)) continue;
			sprintf(s, T2 + "(class \"%s\"\n" + T3, CL.name); t += s;
			for (i = 0; i < j; ++i) {
				if (net_class_number[i] == CL.number) {
					sprintf(s, " \"%s\"", net_names[i]); t += s;
				}
			}
			t += "\n" + T3 + "(rule\n";
			if (CL.width != 0) {
				real w_width = CL.width;
				if (w_width <= 0) w_width = default_wire_width;
				sprintf(s, T4 + "(width %s)\n", num2str(U2G * w_width)); t += s;
			}
			if (CL.clearance != 0) {
				real clearance = CL.clearance;
				if (clearance <= 0) clearance = default_clearance;
				sprintf(s, T4 + "(clearance %s)\n", num2str(U2G * clearance)); t += s;
			}
			t += T3 + ")\n" + T2 + ")\n";
		}
	}
	return t + T1 + ")\n";
}

string Wiring_d(void) {
	string Vname, s, t = T1 + "(wiring\n"; int protect;
	board(B) {
		B.signals(N) {
			N.wires(W) {
				if ((W.layer >= 1) && (W.layer <= 16)) {
					load_one_wire(W);
					sprintf(s, T2 + "(wire\n" + T3 + "(path %s %s ", LN2name(W.layer), num2str(U2G * W.width));
					t += write_wire_shape(s, ") \n", false);
					sprintf(s, "      (net \"%s\")", N.name); t += s;
					protect = max(strstr(wire_protect + ",", N.name + ","), strstr(wire_protect, "*"));
					if (protect >= 0 || (W.style == WIRE_STYLE_SHORTDASH)) t += " (type protect)";
					t += "\n    )\n";
				}
			}
			N.polyPours(P) {
				if ((P.layer == LAYER_TOP) || (P.layer == LAYER_BOTTOM)) {
					protect = max(strstr(poly_drop + ",", N.name + ","), strstr(poly_drop, "*"));
					if (protect >= 0) {
						sprintf(s, T2 + "# Polygon pour with signal %s in layer %d removed on user request\n", N.name, P.layer);
						t += s;
					}
					else {
						string protect_tag = "";
						string pre_wire, post_wire, width_str;
						P.contours(W) {
							load_layer_wires(W, W.layer);
						}
						process_wires();
						if (max(strstr(poly_protect + ",", N.name + ","), strstr(poly_protect, "*")) >= 0) {
							protect_tag = " (type protect)";
						}
						sprintf(width_str, "%s ", num2str(U2G * P.width));
						pre_wire = "    (wire\n      (poly " + LN2name(P.layer) + " " + width_str;
						post_wire = ") \n      (net \"" + N.name + "\")" + protect_tag + "\n    )\n";
						t += write_wire_shape(pre_wire, post_wire, false);
					}
				}
			}
			N.vias(V) {
				Vname = via_sig(V.shape[LAYER_VIAS], V.diameter[LAYER_TOP], V.drill, V.start, V.end);
				sprintf(s, T2 + "(via\n" + T3 + Vname + " %s %s\n" + T3 + "(net \"%s\")",
					num2str(U2G * V.x), num2str(U2G * V.y), N.name);
				t += s;
				protect = max(strstr(via_protect + ",", N.name + ","), strstr(via_protect, "*"));
				if (protect >= 0) t += " (type protect)";
				t += "\n" + T2 + ")\n";
			}
		}
	}
	return t + "  )\n";
}

int do_export_dsn(string target_file) {
	log_info("Starting DSN export to: " + target_file);
	get_designrules();
	default_wire_width = par_value("msWidth");
	default_clearance = par_value("mdWireWire");
	clearance_wire_pad = par_value("mdWirePad");
	clearance_wire_via = par_value("mdWireVia");
	clearance_wire_smd = clearance_wire_pad;
	clearance_pad_pad = par_value("mdPadPad");
	clearance_pad_via = par_value("mdPadVia");
	clearance_via_via = par_value("mdViaVia");
	clearance_smd_pad = par_value("mdSmdPad");
	clearance_smd_via = par_value("mdSmdVia");
	clearance_smd_smd = par_value("mdSmdSmd");
	dim_clearance = par_value("mdCopperDimension");
	default_drill_size = par_value("msDrill");
	min_pad_t = par_value("rlMinPadTop"); rv_pad_t = par_value("rvPadTop"); max_pad_t = par_value("rlMaxPadTop");
	min_pad_i = par_value("rlMinPadInner"); rv_pad_i = par_value("rvPadInner"); max_pad_i = par_value("rlMaxPadInner");
	min_pad_b = par_value("rlMinPadBottom"); rv_pad_b = par_value("rvPadBottom"); max_pad_b = par_value("rlMaxPadBottom");
	min_via_inner = par_value("rlMinViaInner");
	min_via_outer = par_value("rlMinViaOuter");
	default_via_size = default_drill_size + 2 * min_via_outer;

	Lnum = layer_structure(layer_def);
	read_board_pads_and_vias();

	output(target_file, "wt") {
		printf(Parser_d());
		printf(Resolution_d());
		printf(T1 + "(structure\n");
		printf(Layer_d());
		printf(Boundary_d());
		printf(Plane_d());
		printf(Keepout_d());
		printf(Via_d());
		printf(Rule_d());
		printf(T1 + ")\n");
		printf(Placement_d());
		printf(T1 + "(library\n");
		printf(Images_d());
		printf(Padstack_d());
		printf(T1 + ")\n");
		printf(Network_d());
		printf(Wiring_d());
		printf(")");
	};

	log_info("DSN export finished successfully: " + target_file);
	return 1;
}
