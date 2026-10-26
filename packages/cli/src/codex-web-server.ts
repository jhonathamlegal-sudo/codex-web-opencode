import { Effect } from "effect"
import { ServerProcess } from "./server-process"

const password = process.env.OPENCODE_SERVER_PASSWORD
if (!password) throw new Error("OPENCODE_SERVER_PASSWORD is required")

await Effect.runPromise(
  ServerProcess.run({
    mode: "default",
    hostname: "127.0.0.1",
    port: 4096,
  }),
)
