/**
 * run_tests.js
 * 
 * Test runner script:
 * - Executes all unit tests in db_proposal_v2.test.js
 * - Captures test output and execution timings
 * - Exports formatted logs to the "Result" folder under "Unit_test"
 *   (e.g., test_run_YYYYMMDD_HHMMSS.log, latest_test_results.log, test_summary.md)
 */

const fs = require('fs');
const path = require('path');
const { runTestSuite } = require('./db_proposal_v2.test');

const resultDir = path.join(__dirname, 'Result');
if (!fs.existsSync(resultDir)) {
    fs.mkdirSync(resultDir, { recursive: true });
}

// Log collector
const logLines = [];
function logger(msg) {
    console.log(msg);
    logLines.push(msg);
}

const startTime = new Date();
logger(`[${startTime.toISOString()}] Starting Unit Test Execution for DB_proposal_v2.sql...\n`);

let testSummary;
try {
    testSummary = runTestSuite(logger);
} catch (err) {
    logger(`FATAL ERROR DURING TEST EXECUTION: ${err.stack || err.message}`);
    testSummary = { total: 0, passed: 0, failed: 1, results: [] };
}

const endTime = new Date();
const totalDuration = endTime - startTime;
logger(`[${endTime.toISOString()}] Test Execution Complete in ${totalDuration} ms.`);
logger(`Summary: ${testSummary.passed}/${testSummary.total} Passed (${testSummary.failed} Failed)`);

// Generate timestamp for log filename
const pad = n => String(n).padStart(2, '0');
const ts = `${startTime.getFullYear()}${pad(startTime.getMonth() + 1)}${pad(startTime.getDate())}_${pad(startTime.getHours())}${pad(startTime.getMinutes())}${pad(startTime.getSeconds())}`;

const timestampedLogFile = path.join(resultDir, `test_run_${ts}.log`);
const latestLogFile = path.join(resultDir, 'latest_test_results.log');
const summaryMdFile = path.join(resultDir, 'test_summary.md');

const fullLogContent = logLines.join('\n');

// Write log files
fs.writeFileSync(timestampedLogFile, fullLogContent, 'utf8');
fs.writeFileSync(latestLogFile, fullLogContent, 'utf8');

// Generate structured Markdown summary
const mdLines = [
    `# Test Execution Report - DB Proposal v2 Functions`,
    ``,
    `- **Date**: ${startTime.toLocaleString()}`,
    `- **Duration**: ${totalDuration} ms`,
    `- **Total Tests**: ${testSummary.total}`,
    `- **Passed**: ${testSummary.passed}`,
    `- **Failed**: ${testSummary.failed}`,
    `- **Status**: ${testSummary.failed === 0 ? '✅ ALL TESTS PASSED' : '❌ SOME TESTS FAILED'}`,
    ``,
    `## Detailed Test Results`,
    ``,
    `| Group | Test Case | Status | Duration | Error |`,
    `| --- | --- | --- | --- | --- |`
];

for (const r of testSummary.results) {
    const errorText = r.error ? `\`${r.error.replace(/\|/g, '\\|')}\`` : '-';
    const statusIcon = r.status === 'PASSED' ? '✅ PASS' : '❌ FAIL';
    mdLines.push(`| ${r.group} | ${r.name} | ${statusIcon} | ${r.durationMs}ms | ${errorText} |`);
}

mdLines.push(``);
mdLines.push(`## Output Log Files`);
mdLines.push(`- Timestamped Log: \`${path.basename(timestampedLogFile)}\``);
mdLines.push(`- Latest Log: \`${path.basename(latestLogFile)}\``);

fs.writeFileSync(summaryMdFile, mdLines.join('\n'), 'utf8');

logger(`\nLogs exported successfully to:`);
logger(`  -> ${timestampedLogFile}`);
logger(`  -> ${latestLogFile}`);
logger(`  -> ${summaryMdFile}`);

if (testSummary.failed > 0) {
    process.exit(1);
} else {
    process.exit(0);
}

