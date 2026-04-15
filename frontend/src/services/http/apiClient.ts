import type { ApiErrorResponse } from '../../types/api';
import { getAccessToken, invalidateAuthSession } from '../../utils/storage';
import { sanitizeDeep, sanitizeText } from '../../utils/textSanitizer';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export class ApiClientError extends Error {
  status: number;
  field: string | null;
  timestamp: string;

  constructor(payload: ApiErrorResponse) {
    super(payload.message);
    this.name = 'ApiClientError';
    this.status = payload.status;
    this.field = payload.field;
    this.timestamp = payload.timestamp;
  }
}

async function safeJson<T>(response: Response): Promise<T | null> {
  try {
    const text = await response.text();
    if (!text) return null;
    return sanitizeDeep(JSON.parse(text) as T);
  } catch {
    return null;
  }
}

function handleUnauthorizedResponse(status: number, message: string): void {
  if (status !== 401) return;
  const reason = message.toLowerCase().includes('expirou') ? 'expired' : 'unauthorized';
  invalidateAuthSession(reason);
}

function buildConnectionError(): ApiClientError {
  return new ApiClientError({
    message: 'Nao foi possivel conectar ao backend Java.',
    field: null,
    status: 0,
    timestamp: new Date().toISOString(),
  });
}

async function parseJsonResponse<T>(response: Response): Promise<T> {
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return sanitizeDeep(JSON.parse(text) as T);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getAccessToken();
  const headers = new Headers(init?.headers);

  if (!headers.has('Content-Type') && init?.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
    });
  } catch {
    throw buildConnectionError();
  }

  if (!response.ok) {
    const error = (await safeJson<ApiErrorResponse>(response)) ?? {
      message: 'Falha ao processar requisicao.',
      field: null,
      status: response.status,
      timestamp: new Date().toISOString(),
    };

    handleUnauthorizedResponse(response.status, error.message);
    throw new ApiClientError(sanitizeDeep(error));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return parseJsonResponse<T>(response);
}

async function fetchAuthorizedResponse(url: string): Promise<Response> {
  const token = getAccessToken();
  const headers = new Headers();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(url, { method: 'GET', headers });
  } catch {
    throw buildConnectionError();
  }

  if (!response.ok) {
    const error = (await safeJson<ApiErrorResponse>(response)) ?? {
      message: 'Falha ao abrir o arquivo solicitado.',
      field: null,
      status: response.status,
      timestamp: new Date().toISOString(),
    };

    handleUnauthorizedResponse(response.status, error.message);
    throw new ApiClientError(sanitizeDeep(error));
  }

  return response;
}

function extractFileNameFromDisposition(disposition: string | null, fallback: string): string {
  if (!disposition) return fallback;
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1]);
  const simpleMatch = disposition.match(/filename="?([^"]+)"?/i);
  return sanitizeText(simpleMatch?.[1] ?? fallback);
}

function triggerBlobDownload(blobUrl: string, fileName: string): void {
  const link = document.createElement('a');
  link.href = blobUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};

export async function uploadAuthorizedFile<T>(
  path: string,
  file: File,
  fieldName = 'file'
): Promise<T> {
  const token = getAccessToken();
  const formData = new FormData();
  formData.append(fieldName, file);

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      body: formData,
    });
  } catch {
    throw buildConnectionError();
  }

  if (!response.ok) {
    const error = (await safeJson<ApiErrorResponse>(response)) ?? {
      message: 'Falha ao enviar o arquivo.',
      field: null,
      status: response.status,
      timestamp: new Date().toISOString(),
    };

    handleUnauthorizedResponse(response.status, error.message);
    throw new ApiClientError(sanitizeDeep(error));
  }

  return parseJsonResponse<T>(response);
}

export async function fetchAuthorizedBlobUrl(path: string): Promise<string | null> {
  const response = await fetchAuthorizedResponse(`${API_BASE_URL}${path}`);
  const blob = await response.blob();
  if (!blob.size) return null;
  return URL.createObjectURL(blob);
}

export function getApiBaseUrl() {
  return API_BASE_URL;
}

export function getItFileUrl(itId: string): string {
  return `${API_BASE_URL}/it/${itId}/file`;
}

export async function openProtectedItFile(itIdOrUrl: string): Promise<void> {
  const url = itIdOrUrl.startsWith('http') ? itIdOrUrl : getItFileUrl(itIdOrUrl);
  const response = await fetchAuthorizedResponse(url);
  const blob = await response.blob();
  const blobUrl = URL.createObjectURL(blob);
  window.open(blobUrl, '_blank', 'noopener,noreferrer');
  window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
}

export async function downloadProtectedItFile(
  itIdOrUrl: string,
  suggestedFileName = 'Documento IT.pdf'
): Promise<void> {
  const url = itIdOrUrl.startsWith('http') ? itIdOrUrl : getItFileUrl(itIdOrUrl);
  const response = await fetchAuthorizedResponse(url);
  const blob = await response.blob();
  const fileName = extractFileNameFromDisposition(
    response.headers.get('content-disposition'),
    suggestedFileName
  );
  const blobUrl = URL.createObjectURL(blob);
  triggerBlobDownload(blobUrl, fileName);
  window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
}
