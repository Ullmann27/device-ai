#!/usr/bin/env node
/** Separate owner-started, loopback-only read observer. No task writes or unattended network configuration. */
import fs from 'node:fs';
import path from 'node:path';
import { createCodingMcpToken, startCodingMcpServer } from '../.github/continuum/coding-mcp-server.mjs';
import { createCodingMcpReader } from '../.github/continuum/coding-mcp-reader.mjs';

const usage = `Usage:
  node scripts/continuum-coding-mcp.mjs init-token --token-file /private/owner-mcp/token
  node scripts/continuum-coding-mcp.mjs serve --config /private/service/config.json --token-file /private/owner-mcp/token [--port 8766]

Create the token parent directory first with mode 0700. The generated credential is never printed.
The credential must remain outside source, project, profiles and service state directories.
serve stays in the foreground. SIGINT/SIGTERM stops only this observer, never a coding job.
Transport: MCP 2025-06-18 Streamable HTTP at 127.0.0.1 only, local pre-shared bearer authentication.
No OAuth discovery, public endpoint or ChatGPT connection is configured. No write tools exist.
`;

async function main() {
  const argv = process.argv.slice(2), command = argv.shift();
  if (command === '--help' || command === 'help' || !command) { process.stdout.write(usage); return; }
  const options = {};
  while (argv.length) {
    const key = argv.shift(), value = argv.shift();
    if (!['--config', '--token-file', '--port'].includes(key) || value === undefined || Object.hasOwn(options, key)) throw new Error('MCP_CLI_ARGUMENTS_INVALID');
    options[key] = value;
  }
  const tokenFile = options['--token-file'];
  if (!tokenFile || !path.isAbsolute(tokenFile)) throw new Error('MCP_CLI_CREDENTIAL_REQUIRED');
  if (command === 'init-token') {
    if (Object.keys(options).length !== 1) throw new Error('MCP_CLI_ARGUMENTS_INVALID');
    process.stdout.write(JSON.stringify(createCodingMcpToken({ tokenFile })) + '\n'); return;
  }
  if (command !== 'serve' || !options['--config'] || !path.isAbsolute(options['--config'])) throw new Error('MCP_CLI_ARGUMENTS_INVALID');
  const configPath = options['--config'];
  if (fs.realpathSync(configPath) !== configPath) throw new Error('MCP_CONFIG_SYMLINK');
  const { loadCodingServiceConfig } = await import('../.github/continuum/coding-service-store.mjs');
  const config = loadCodingServiceConfig(configPath);
  const protectedRoots = [config.sourceRoot, config.project, config.profileDirectory, config.stateDir];
  if (protectedRoots.some(root => typeof root !== 'string' || !path.isAbsolute(root))) throw new Error('MCP_CONFIG_ROOTS_INVALID');
  const portText = options['--port'] ?? '8766';
  if (!/^\d{1,5}$/.test(portText)) throw new Error('MCP_CLI_PORT_INVALID');
  const reader = createCodingMcpReader({ configPath });
  let server;
  try { server = await startCodingMcpServer({ reader, tokenFile, protectedRoots, port: Number(portText) }); }
  catch (error) { await reader.close(); throw error; }
  process.stdout.write(JSON.stringify({ status: 'LISTENING', url: server.url, tools: ['get_status', 'list_agents', 'list_tasks', 'get_evidence'], auth: 'LOCAL_OWNER_BEARER', chatgptConnected: false, environment: 'HOST', deviceVerified: false }) + '\n');
  let stopping = false;
  const stop = async () => { if (stopping) return; stopping = true; await server.close(); process.stdout.write('{"status":"STOPPED"}\n'); };
  process.once('SIGINT', stop); process.once('SIGTERM', stop);
}

main().catch(() => { process.stderr.write('MCP_START_FAILED: verify owner-only paths, canonical service configuration and local port.\n'); process.exitCode = 1; });
