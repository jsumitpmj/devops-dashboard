{{/*
=============================================================================
DevOps Dashboard — Helm Chart Helpers
=============================================================================
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "devops-dashboard.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "devops-dashboard.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart label.
*/}}
{{- define "devops-dashboard.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Resolve the target namespace.
*/}}
{{- define "devops-dashboard.namespace" -}}
{{- if .Values.global.namespaceOverride }}
{{- .Values.global.namespaceOverride }}
{{- else if .Values.namespace.name }}
{{- .Values.namespace.name }}
{{- else }}
{{- .Release.Namespace }}
{{- end }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "devops-dashboard.labels" -}}
helm.sh/chart: {{ include "devops-dashboard.chart" . }}
app.kubernetes.io/part-of: devops-dashboard
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels for a given component name.
Usage: {{ include "devops-dashboard.selectorLabels" (dict "name" "api") }}
*/}}
{{- define "devops-dashboard.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
{{- end }}

{{/*
Build the full image reference, honouring global.imageRegistry.
Usage: {{ include "devops-dashboard.image" (dict "registry" .Values.global.imageRegistry "image" .Values.api.image) }}
*/}}
{{- define "devops-dashboard.image" -}}
{{- $registry := .registry }}
{{- $repo := .image.repository }}
{{- $tag := .image.tag | default "latest" }}
{{- if $registry }}
{{- printf "%s/%s:%s" $registry $repo $tag }}
{{- else }}
{{- printf "%s:%s" $repo $tag }}
{{- end }}
{{- end }}

{{/*
ServiceAccount name.
*/}}
{{- define "devops-dashboard.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "devops-dashboard.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Standard liveness/readiness/startup HTTP probes for backend services.
Usage: {{ include "devops-dashboard.httpProbes" (dict "path" "/health" "port" "http") }}
*/}}
{{- define "devops-dashboard.httpProbes" -}}
livenessProbe:
  httpGet:
    path: {{ .path | default "/health" }}
    port: {{ .port | default "http" }}
  initialDelaySeconds: 45
  periodSeconds: 15
  timeoutSeconds: 5
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: {{ .path | default "/health" }}
    port: {{ .port | default "http" }}
  initialDelaySeconds: 20
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3
startupProbe:
  httpGet:
    path: {{ .path | default "/health" }}
    port: {{ .port | default "http" }}
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 15
{{- end }}

{{/*
Standard frontend liveness/readiness probes (no startup probe, lighter delay).
*/}}
{{- define "devops-dashboard.frontendProbes" -}}
livenessProbe:
  httpGet:
    path: /
    port: http
  initialDelaySeconds: 15
  periodSeconds: 15
  timeoutSeconds: 5
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: /
    port: http
  initialDelaySeconds: 10
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3
{{- end }}

{{/*
Rolling update strategy (used by api, auth, deployment-svc, notification, frontend).
*/}}
{{- define "devops-dashboard.rollingStrategy" -}}
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0
{{- end }}
