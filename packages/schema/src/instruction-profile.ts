export * as InstructionProfile from "./instruction-profile.js"

import { Model } from "./model.js"
import { Provider } from "./provider.js"
import { Schema } from "effect"

export const Scope = Schema.Literals(["global", "model"])
export type Scope = typeof Scope.Type

export const ModelRef = Schema.Struct({
  providerID: Provider.ID,
  modelID: Model.ID,
})
export type ModelRef = typeof ModelRef.Type

export const Document = Schema.Struct({
  scope: Scope,
  providerID: Provider.ID.pipe(Schema.optionalKey),
  modelID: Model.ID.pipe(Schema.optionalKey),
  path: Schema.String,
  content: Schema.String,
  exists: Schema.Boolean,
  revision: Schema.String,
  updatedAt: Schema.String.pipe(Schema.optionalKey),
}).annotate({ identifier: "InstructionProfile.Document" })
export type Document = typeof Document.Type

export const HistoryEntry = Schema.Struct({
  id: Schema.String,
  scope: Scope,
  providerID: Provider.ID.pipe(Schema.optionalKey),
  modelID: Model.ID.pipe(Schema.optionalKey),
  path: Schema.String,
  content: Schema.String,
  createdAt: Schema.String,
}).annotate({ identifier: "InstructionProfile.HistoryEntry" })
export type HistoryEntry = typeof HistoryEntry.Type

export const Write = Schema.Struct({ content: Schema.String })
export type Write = typeof Write.Type

export const HistoryQuery = Schema.Struct({
  scope: Scope,
  providerID: Provider.ID.pipe(Schema.optionalKey),
  modelID: Model.ID.pipe(Schema.optionalKey),
})
export type HistoryQuery = typeof HistoryQuery.Type

export const Restore = Schema.Struct({
  scope: Scope,
  providerID: Provider.ID.pipe(Schema.optionalKey),
  modelID: Model.ID.pipe(Schema.optionalKey),
  historyID: Schema.String,
})
export type Restore = typeof Restore.Type
