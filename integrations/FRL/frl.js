#!/usr/bin/env node
const yargs = require('yargs/yargs');
const {hideBin} = require('yargs/helpers');
const arg = hideBin(process.argv);
const cli = yargs(arg);
const os = require('node:os'); 
const fs = require('node:fs');
const http = require('http');
const WebSocket = require('ws');
const WebSocketServer = WebSocket.Server;
const readline = require("readline");
var progress = require('progress');
const express = require('express');
const proxy = require('http-proxy-middleware');
const { spawn, execSync } = require('node:child_process');

var argv = cli.usage('Usage: $0 --jar=<freerouting jar file> [options]')
.epilogue(`This script was made by L1uTongwei<1347277058@qq.com>.`).wrap(yargs.terminalWidth).alias('h', 'help')
.option('executable', {type: "string", describe: "Path to freerouting executable jar File.", alias: 'jar'})
.option('deliver', {type: 'array', describe: 'Deliver options to freerouting.', alias: 'd'})
.default('lceda', false).option('lceda', {type: 'boolean', describe: 'Create an EasyEDA Auto-router Server.'})
.default('data', '.data.tmp').option('data', {type: 'string', describe: 'Set freerouting data file (-op). Do not set -op in --deliver. Only for --exlog.'})
.default('gui', false).option('gui', {type: "boolean", describe: 'Launch freerouting with GUI. Force enabled in Windows.'})
.default('Xaddr', ':95').option('Xaddr', {type: "string", describe: "(Optional) Designate the X11 address for Xvfb. This will be ignored by --gui option."})
.default('java', 'java').option('java', {type: 'string', describe: '(Optional) Designate the path to java.'})
.default('Xvfb', 'Xvfb').option('Xvfb', {type: 'string', describe: '(Optional) Designate the path to Xvfb.'})
.default('debug', false).option('debug', {type: 'boolean', describe: 'Output more debug information.'})
.default('old', false).option('old', {type: 'boolean', describe: 'Reverse old parser for old freerouting.'})
.example('', 'frl --jar=<freerouting jar file> --deliver de=<input file> do=<output file>').argv;
if(argv.debug) console.log(argv);
if(argv.old && argv.lceda){
    console.log("Error: Conflicts option: --old and --lceda.");
    console.error("Use --help to see help message.");
    process.exit(1);
}

//Child Processes
var freerouting, Xvfb;
if(!argv.executable){
    console.error("Error: No freerouting executable jar file designated.");
    console.error("Use --help to see help message.");
    process.exit(1);
}
var execute_argv = ["-jar", argv.executable];

//This judge and below judge just for prevent RCE
try{ 
    if(argv.java != 'java' && !fs.existsSync(argv.java)) throw "";
    execSync("which " + argv.java);
}
catch(e){
    console.error("Invaild java path designated.");
    console.error("Use --help to see help message.");
    process.exit(1);
}

if(os.type() == "Windows_NT") argv.gui = true;
if(!argv.gui){
    try{ 
        if(argv.Xvfb != 'Xvfb' && !fs.existsSync(argv.Xvfb)) throw "";
        execSync("which " + argv.Xvfb);
    }
    catch(e){
        console.error("No Xvfb command found.");
        console.error("Please install Xvfb package in your system, or use --gui option.");
        console.error("Use --help to see help message.");
        process.exit(1);
    }
    if(argv.debug) console.log("Running Xvfb as: " + argv.Xvfb + " " + argv.Xaddr);
    Xvfb = spawn(argv.Xvfb, [argv.Xaddr]);
    Xvfb.unref();
}

//Parse deliver array
var deliver = argv.deliver;
for(var i = 0; i < deliver.length; i++){
    if(!deliver[i] || deliver == " ") continue;
    deliver[i] = "-" + deliver[i];
    deliver[i] = deliver[i].replace("=", " ");
}
execute_argv = execute_argv.concat(deliver);
if(!argv.old){
    execute_argv.push('-df');
    if(argv.data) execute_argv.push('-op ' + argv.data);
}
execute_argv.push('-dct 0');

function runFreerouting(ws){
    //Run freerouting
    var iarr = execute_argv;
    if(ws) iarr.push('-de .tmp.dsn');

    if(argv.debug) console.log(`Current directory: ${process.cwd()}`);
    if(argv.debug && !argv.gui) console.log("Running freerouting as: DISPLAY=" + argv.Xaddr + " " + argv.java + " " + execute_argv.join(' '));
    else if(argv.debug) console.log("Running freerouting as: " + argv.java + " " + execute_argv.join(' '));
    if(!argv.gui) freerouting = spawn(argv.java, execute_argv, {env: {DISPLAY: argv.Xaddr}, cwd: process.cwd(), stdio: "pipe", shell: true});
    else freerouting = spawn(argv.java, execute_argv, {cwd: process.cwd(), stdio: "pipe", shell: true});

    //Parse log and display progress
    var pipe = readline.createInterface({
        input: freerouting.stdout,
        output: null
    });
    var errpipe = readline.createInterface({
        input: freerouting.stderr,
        output: null
    });
    var total = 0, result = 0, bar;
    pipe.on('line', (line) => {
        console.log(line);
        if(!argv.old){
            var log = line.split(' ');
            //Before auto-routing
            if(log[3] == "INFO" && log[5] == "Before" && log[6] == "route:"){
                total = parseInt(log[7]);
                bar = new ProgressBar("Routing [:bar] :percent", {
                    stream: process.stdout,
                    complete: '=',
                    incomplete: ' ',
                    width: yargs.terminalWidth - 19,
                    clear: true,
                    total: total + 1
                });
            }
            //In auto-routing
            if(log[3] == "INFO" && log[5] == "Auto-router" && log[6] == "pass"){
                var prog = parseInt(log[9]);
                if(argv.lceda) ws.send(JSON.stringify({a: "routingProgress", inCompleteNetNum: prog, data: fs.readFileSync(argv.data)}) + '\n');
                bar.tick(prog);
            }
            //After auto-routing
            if(log[3] == "INFO" && log[5] == "After" && log[6] == "route:"){
                result = parseInt(log[7]);
                if(argv.lceda) ws.send(JSON.stringify({a: "routingProgress", inCompleteNetNum: result, data: fs.readFileSync(argv.data)}) + '\n');
                bar.tick(result);
            }
            //After optimiztion
            if(log[3] == "INFO" && log[5] == "Route" && log[6] == "optimization" && log[7] == "was" && log[8] == "completed"){
                if(argv.lceda) ws.send(JSON.stringify({a: "routingResult", inCompleteNetNum: result, complete: parseInt(result == 0), data: fs.readFileSync(argv.data)}) + '\n');
                bar.tick(result + 1);
                process.stdout.write('Freerouting Laucher: Result ' + toString(result) + '/' + toString(total) + '\n');
                if(ws) fs.rmSync('.tmp.dsn');
            }
        }
    });
    errpipe.on('line', (line) => {
        console.log(line);
    });
}
if(!argv.lceda){
    runFreerouting();
    freerouting.on('exit', (code) => {
        if(!argv.old) fs.rmSync(argv.data);
        Xvfb.kill('SIGINT');
        process.exit(code);
    });
}
else{
    var app = express();
    var wsServer = new WebSocketServer({port: 3580});
    wsServer.on('connection', (ws, req) => {
        ws.on('message', (data) => {
            var json = JSON.parse(data);
            if(json.a == "startRoute" && !freerouting.connected){
                fs.writeFileSync(".tmp.dsn", json.data);
                runFreerouting(ws);
            }
        });
    });
    app.get('/api/whois', (req, res) => {
        res.send("EasyEDA Auto Router");
    });
    app.use(proxy('/router', {target: "wss://127.0.0.1:3580"}));
    app.listen(3579);
}