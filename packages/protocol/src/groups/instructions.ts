import { InstructionProfile } from "@opencode/schema/instruction-profile"
import { Location } from "@opencode/schema/location"
import { Context, Schema } from "effect"
import { HttpApiEndpoint, HttpApiGroup, HttpApiMiddleware, OpenApi } from "effect/http-api"
import { LocationQuery, locationQueryOpenApi } from "./location.js"

const ModelParams = {
  providerID: Schema.String,
  modelID: Schema.String,
}

const HistoryQuery = Schema.Struct({
  ...LocationQuery.fields,
  scope: InstructionProfile.Scope,
  providerID: Schema.optional(Schema.String),
  modelID: Schema.optional(Schema.String),
})

export const makeInstructionsGroup = <LocationId extends HttpApiMiddleware.AnyId, LocationService>(
  locationMiddleware: Context.Key<LocationId, LocationService>,
) =>
  HttpApiGroup.make("server.instructions")
    .add(
      HttpApiEndpoint.get("instructions.global.get", "/api/instructions/global", {
        query: LocationQuery,
        success: Location.response(InstructionProfile.Document),
      })
        .middleware(locationMiddleware)
        .annotateMerge(locationQueryOpenApi)
        .annotateMerge(
          OpenApi.annotations({
            identifier: "instructions.global.get",
            summary: "Read global instructions",
            description: "Read the project AGENTS.md document used by OpenCode.",
          }),
        ),
    )
    .add(
      HttpApiEndpoint.put("instructions.global.save", "/api/instructions/global", {
        query: LocationQuery,
        payload: InstructionProfile.Write,
        success: Location.response(InstructionProfile.Document),
      })
        .middleware(locationMiddleware)
        .annotateMerge(locationQueryOpenApi)
        .annotateMerge(
          OpenApi.annotations({
            identifier: "instructions.global.save",
            summary: "Save global instructions",
            description: "Persist the project AGENTS.md document and retain a history snapshot.",
          }),
        ),
    )
    .add(
      HttpApiEndpoint.get("instructions.model.get", "/api/instructions/model/:providerID/:modelID", {
        params: ModelParams,
        query: LocationQuery,
        success: Location.response(InstructionProfile.Document),
      })
        .middleware(locationMiddleware)
        .annotateMerge(locationQueryOpenApi)
        .annotateMerge(
          OpenApi.annotations({
            identifier: "instructions.model.get",
            summary: "Read model instructions",
            description: "Read the AGENT.md document dedicated to one provider/model pair.",
          }),
        ),
    )
    .add(
      HttpApiEndpoint.put("instructions.model.save", "/api/instructions/model/:providerID/:modelID", {
        params: ModelParams,
        query: LocationQuery,
        payload: InstructionProfile.Write,
        success: Location.response(InstructionProfile.Document),
      })
        .middleware(locationMiddleware)
        .annotateMerge(locationQueryOpenApi)
        .annotateMerge(
          OpenApi.annotations({
            identifier: "instructions.model.save",
            summary: "Save model instructions",
            description: "Persist one model's AGENT.md without modifying other model profiles.",
          }),
        ),
    )
    .add(
      HttpApiEndpoint.get("instructions.history", "/api/instructions/history", {
        query: HistoryQuery,
        success: Location.response(Schema.Array(InstructionProfile.HistoryEntry)),
      })
        .middleware(locationMiddleware)
        .annotateMerge(locationQueryOpenApi)
        .annotateMerge(
          OpenApi.annotations({
            identifier: "instructions.history",
            summary: "List instruction history",
            description: "List recoverable previous versions of a global or model instruction file.",
          }),
        ),
    )
    .add(
      HttpApiEndpoint.post("instructions.restore", "/api/instructions/restore", {
        query: LocationQuery,
        payload: InstructionProfile.Restore,
        success: Location.response(Schema.Union([InstructionProfile.Document, Schema.Undefined])),
      })
        .middleware(locationMiddleware)
        .annotateMerge(locationQueryOpenApi)
        .annotateMerge(
          OpenApi.annotations({
            identifier: "instructions.restore",
            summary: "Restore instruction history",
            description: "Restore a previous version of AGENTS.md or a model AGENT.md.",
          }),
        ),
    )
    .annotateMerge(OpenApi.annotations({ title: "instructions", description: "Persistent model instruction profiles." }))

