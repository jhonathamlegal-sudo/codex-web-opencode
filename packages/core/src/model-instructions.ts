export * as ModelInstructions from "./model-instructions.js"

import os from "node:os"
import path from "node:path"
import { createHash } from "node:crypto"
import { Context, Effect, Layer } from "effect"
import { makeGlobalNode } from "@opencode/util/effect/app-node"
import { FSUtil } from "@opencode/util/fs-util"
import { Global } from "@opencode/util/global"
import { InstructionProfile } from "@opencode/schema/instruction-profile"

export type Scope = InstructionProfile.Scope
export type ModelRef = InstructionProfile.ModelRef
export type Document = InstructionProfile.Document
export type HistoryEntry = InstructionProfile.HistoryEntry

export interface Interface {
  readonly global: () => Effect.Effect<Document>
  readonly model: (ref: ModelRef) => Effect.Effect<Document>
  readonly load: (ref: ModelRef) => Effect.Effect<string | undefined>
  readonly save: (scope: Scope, ref: ModelRef | undefined, content: string) => Effect.Effect<Document>
  readonly history: (scope: Scope, ref: ModelRef | undefined) => Effect.Effect<HistoryEntry[]>
  readonly restore: (
    scope: Scope,
    ref: ModelRef | undefined,
    historyID: string,
  ) => Effect.Effect<Document | undefined>
}

export class Service extends Context.Service<Service, Interface>()("@opencode/ModelInstructions") {}

const defaultGlobal = () => {
  const prefix = process.env.PREFIX
  return `# AGENTS.md

## Identidade e propósito

Este é o AGENTS.md real do projeto OpenCode. Ele é lido diretamente pelo agente e pode ser editado pelo usuário no aplicativo.

## Runtime detectado

- Sistema operacional: ${process.platform}
- Arquitetura: ${process.arch}
- Versão do runtime: ${process.version}
- Kernel: ${os.release()}
- Diretório inicial: ${os.homedir()}
- Prefixo Termux: ${prefix ?? "não detectado"}

## Componentes e integrações

- OpenCode executa localmente e atende a interface web por um servidor HTTP local.
- Termux é considerado disponível quando o processo possui um prefixo Termux detectável; a acessibilidade de Termux:API, Termux:Boot e Termux:X11 deve ser verificada antes de ser declarada funcional.
- Ferramentas, pacotes, permissões e serviços adicionais devem ser documentados somente após verificação no dispositivo.

## Preferência compartilhada de qualidade

Analise requisitos, dependências, alternativas e efeitos colaterais antes de agir. Prefira soluções completas, verificáveis e sustentáveis. Execute testes viáveis e declare claramente limitações, incertezas e fatos ainda não verificados.

## Arquitetura de interação

O aplicativo web conversa com o servidor local. O servidor resolve o modelo selecionado e combina este arquivo do projeto com o AGENT.md específico do modelo antes de preparar a requisição ao provedor.
`
}

const segment = (value: string) => encodeURIComponent(value)

const hash = (content: string) => createHash("sha256").update(content).digest("hex")

const timestamp = (id: string) => {
  const value = Number(id.split("-", 1)[0])
  return Number.isFinite(value) ? new Date(value).toISOString() : new Date().toISOString()
}

export const layer = Layer.effect(
  Service,
  Effect.gen(function* () {
    const global = yield* Global.Service
    const fs = yield* FSUtil.Service
    // The server is launched from the OpenCode project root by termux-start.sh.
    // Keep the editor pointed at the same file that project instruction discovery reads.
    const projectRoot = path.resolve(process.cwd())
    const globalPath = path.join(projectRoot, "AGENTS.md")
    const modelPath = (ref: ModelRef) =>
      path.join(global.config, "models", segment(ref.providerID), segment(ref.modelID), "AGENT.md")
    const historyPath = (scope: Scope, ref?: ModelRef) => {
      if (scope === "global") return path.join(global.config, "instruction-history", "global")
      if (!ref) throw new Error("Model instructions require a model reference")
      return path.join(
        global.config,
        "instruction-history",
        "models",
        segment(ref.providerID),
        segment(ref.modelID),
      )
    }
    const targetPath = (scope: Scope, ref?: ModelRef) => (scope === "global" ? globalPath : modelPath(ref!))

    const read = Effect.fn("ModelInstructions.read")(function* (scope: Scope, ref?: ModelRef) {
      const target = targetPath(scope, ref)
      const content = yield* fs.readFileStringSafe(target).pipe(Effect.orElseSucceed(() => undefined))
      const exists = content !== undefined
      return {
        scope,
        ...(scope === "model" ? { providerID: ref!.providerID, modelID: ref!.modelID } : {}),
        path: target,
        content: content ?? "",
        exists,
        revision: content === undefined ? "" : hash(content),
      } satisfies Document
    })

    const atomicWrite = Effect.fn("ModelInstructions.atomicWrite")(function* (target: string, content: string) {
      const temporary = `${target}.tmp-${process.pid}-${Date.now()}`
      yield* fs.writeWithDirs(temporary, content, 0o600)
      yield* fs.rename(temporary, target).pipe(Effect.ensuring(fs.remove(temporary, { force: true }).pipe(Effect.ignore)))
    })

    const globalDocument = yield* read("global")
    if (!globalDocument.exists) yield* atomicWrite(globalPath, defaultGlobal()).pipe(Effect.ignore)

    const document = (scope: Scope, ref?: ModelRef) => read(scope, ref)

    const save = Effect.fn("ModelInstructions.save")(function* (
      scope: Scope,
      ref: ModelRef | undefined,
      content: string,
    ) {
      const current = yield* document(scope, ref)
      if (current.exists && current.content !== content) {
        const id = `${Date.now()}-${current.revision.slice(0, 12) || "empty"}.md`
        yield* fs.writeWithDirs(path.join(historyPath(scope, ref), id), current.content, 0o600)
      }
      yield* atomicWrite(targetPath(scope, ref), content)
      return yield* document(scope, ref)
    })

    const history = Effect.fn("ModelInstructions.history")(function* (scope: Scope, ref?: ModelRef) {
      const directory = historyPath(scope, ref)
      const entries = yield* fs.readDirectoryEntries(directory).pipe(Effect.orElseSucceed(() => []))
      return yield* Effect.forEach(
        entries
          .filter((entry) => entry.type === "file" && entry.name.endsWith(".md"))
          .sort((a, b) => b.name.localeCompare(a.name)),
        (entry) =>
          fs.readFileString(path.join(directory, entry.name)).pipe(
            Effect.map((content) => ({
              id: entry.name.slice(0, -3),
              scope,
              ...(scope === "model" ? { providerID: ref!.providerID, modelID: ref!.modelID } : {}),
              path: path.join(directory, entry.name),
              content,
              createdAt: timestamp(entry.name),
            }) satisfies HistoryEntry),
            Effect.orElseSucceed(() => undefined),
          ),
        { concurrency: "unbounded" },
      ).pipe(Effect.map((items) => items.filter((item): item is HistoryEntry => item !== undefined).slice(0, 50)))
    })

    const restore = Effect.fn("ModelInstructions.restore")(function* (
      scope: Scope,
      ref: ModelRef | undefined,
      historyID: string,
    ) {
      if (!/^[0-9]+-[a-f0-9]+$/.test(historyID)) return undefined
      const entry = yield* fs
        .readFileString(path.join(historyPath(scope, ref), `${historyID}.md`))
        .pipe(Effect.option)
      if (entry._tag === "None") return undefined
      return yield* save(scope, ref, entry.value)
    })

    return Service.of({
      global: () => document("global"),
      model: (ref) => document("model", ref),
      load: (ref) => document("model", ref).pipe(Effect.map((item) => (item.exists ? item.content : undefined))),
      save: (scope, ref, content) => save(scope, ref, content).pipe(Effect.orDie),
      history,
      restore: (scope, ref, historyID) => restore(scope, ref, historyID).pipe(Effect.orDie),
    })
  }),
)

export const node = makeGlobalNode({ service: Service, layer, deps: [FSUtil.node, Global.node] })
