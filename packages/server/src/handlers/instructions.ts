import { ModelInstructions } from "@opencode/core/model-instructions"
import { Model } from "@opencode/schema/model"
import { Provider } from "@opencode/schema/provider"
import { Effect } from "effect"
import { HttpApiBuilder } from "effect/http-api"
import { Api } from "../api"
import { response } from "../location"

const ref = (providerID: string, modelID: string) => ({
  providerID: Provider.ID.make(providerID),
  modelID: Model.ID.make(modelID),
})

export const InstructionsHandler = HttpApiBuilder.group(Api, "server.instructions", (handlers) =>
  Effect.gen(function* () {
    const profiles = yield* ModelInstructions.Service
    return handlers
      .handle("instructions.global.get", () => response(profiles.global()))
      .handle(
        "instructions.global.save",
        Effect.fn(function* (ctx) {
          return yield* response(profiles.save("global", undefined, ctx.payload.content))
        }),
      )
      .handle(
        "instructions.model.get",
        Effect.fn(function* (ctx) {
          return yield* response(profiles.model(ref(ctx.params.providerID, ctx.params.modelID)))
        }),
      )
      .handle(
        "instructions.model.save",
        Effect.fn(function* (ctx) {
          return yield* response(
            profiles.save("model", ref(ctx.params.providerID, ctx.params.modelID), ctx.payload.content),
          )
        }),
      )
      .handle(
        "instructions.history",
        Effect.fn(function* (ctx) {
          const model = ctx.query.providerID && ctx.query.modelID ? ref(ctx.query.providerID, ctx.query.modelID) : undefined
          return yield* response(profiles.history(ctx.query.scope, model))
        }),
      )
      .handle(
        "instructions.restore",
        Effect.fn(function* (ctx) {
          const model = ctx.payload.providerID && ctx.payload.modelID
            ? ref(ctx.payload.providerID, ctx.payload.modelID)
            : undefined
          return yield* response(profiles.restore(ctx.payload.scope, model, ctx.payload.historyID))
        }),
      )
  }),
)
