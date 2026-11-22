import { readdir, readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Pool } from "pg";

if (!process.env.DATABASE_URL) throw new Error("DATABASE_URL is required.");
const directory = join(dirname(fileURLToPath(import.meta.url)), "..", "migrations");
const pool = new Pool({ connectionString: process.env.DATABASE_URL, max: 2 });

try {
  await pool.query(`CREATE TABLE IF NOT EXISTS aura_schema_migrations (
    name TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  )`);
  const migrations = (await readdir(directory)).filter(name => name.endsWith(".sql")).sort();
  for (const name of migrations) {
    const exists = await pool.query("SELECT 1 FROM aura_schema_migrations WHERE name = $1", [name]);
    if (exists.rowCount) continue;
    const sql = await readFile(join(directory, name), "utf8");
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(sql);
      await client.query("INSERT INTO aura_schema_migrations (name) VALUES ($1)", [name]);
      await client.query("COMMIT");
      process.stdout.write(`Applied ${name}\n`);
    } catch (error) {
      await client.query("ROLLBACK").catch(() => {});
      throw error;
    } finally {
      client.release();
    }
  }
  process.stdout.write("AURA recovery database is up to date.\n");
} finally {
  await pool.end();
}
