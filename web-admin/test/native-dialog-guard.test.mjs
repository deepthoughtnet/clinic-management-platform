import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import ts from "typescript";

const ROOT = path.join(process.cwd(), "src");
const BANNED = new Set(["alert", "prompt", "confirm"]);

function listSourceFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...listSourceFiles(full));
    } else if (/\.(ts|tsx|js|jsx)$/.test(entry.name)) {
      files.push(full);
    }
  }
  return files;
}

function scanFile(fileName) {
  const sourceText = fs.readFileSync(fileName, "utf8");
  const sourceFile = ts.createSourceFile(fileName, sourceText, ts.ScriptTarget.Latest, true, fileName.endsWith(".tsx") ? ts.ScriptKind.TSX : fileName.endsWith(".ts") ? ts.ScriptKind.TS : ts.ScriptKind.JS);
  const violations = [];

  function visit(node) {
    if (ts.isCallExpression(node)) {
      const callee = node.expression;
      if (ts.isIdentifier(callee) && BANNED.has(callee.text)) {
        const { line, character } = sourceFile.getLineAndCharacterOfPosition(callee.getStart(sourceFile));
        violations.push({ line: line + 1, column: character + 1, call: callee.text });
      }
      if (ts.isPropertyAccessExpression(callee)) {
        const object = callee.expression;
        if (ts.isIdentifier(object) && (object.text === "window" || object.text === "globalThis") && BANNED.has(callee.name.text)) {
          const { line, character } = sourceFile.getLineAndCharacterOfPosition(callee.getStart(sourceFile));
          violations.push({ line: line + 1, column: character + 1, call: `${object.text}.${callee.name.text}` });
        }
      }
    }
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return violations;
}

test("web-admin source does not use native browser dialogs", () => {
  const files = listSourceFiles(ROOT);
  const violations = [];
  for (const file of files) {
    violations.push(...scanFile(file).map((entry) => ({ file, ...entry })));
  }

  assert.equal(violations.length, 0, `Native dialogs found:\n${violations.map((v) => `${path.relative(process.cwd(), v.file)}:${v.line}:${v.column} -> ${v.call}()`).join("\n")}`);
});
