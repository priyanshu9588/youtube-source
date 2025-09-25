// Simple test for the Node.js executor
const readline = require('readline');

// Test request
const testRequest = {
    type: "generic",
    input: "test123",
    functionCode: "function reverseString(s) { return s.split('').reverse().join(''); }"
};

console.log("Testing Node.js executor with request:");
console.log(JSON.stringify(testRequest));

// Send to the executor script
const { spawn } = require('child_process');
const path = require('path');

const scriptPath = path.join(__dirname, 'common/src/main/resources/youtube-cipher-executor.js');
const nodeProcess = spawn('node', [scriptPath]);

nodeProcess.stdout.on('data', (data) => {
    console.log('Response:', data.toString());
    nodeProcess.kill();
});

nodeProcess.stderr.on('data', (data) => {
    console.error('Error:', data.toString());
});

nodeProcess.on('close', (code) => {
    console.log(`Process exited with code ${code}`);
});

// Send the test request
nodeProcess.stdin.write(JSON.stringify(testRequest) + '\n');