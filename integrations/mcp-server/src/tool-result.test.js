const assert = require('node:assert/strict');
const test = require('node:test');
const { buildToolResult } = require('./index.js');

test('successful local file tool matches the {message} outputSchema', () => {
  const body = { message: 'Successfully uploaded input from file: board.dsn' };
  const result = buildToolResult(200, body, false);
  assert.equal(result.isError, false);
  assert.deepEqual(result.structuredContent, body);
  const text = JSON.parse(result.content[0].text);
  assert.equal(text.status, 200);
  assert.equal(text.contentType, 'application/json');
  assert.deepEqual(text.body, body);
});

test('error result omits structuredContent that would fail the outputSchema', () => {
  const body = { detail: 'job has not started yet' };
  const result = buildToolResult(400, body, true);
  assert.equal(result.isError, true);
  assert.equal(Object.hasOwn(result, 'structuredContent'), false);
  const text = JSON.parse(result.content[0].text);
  assert.deepEqual(text.body, body);
});
