import { apiBaseUrl } from '../config/api.js';

const INGESTION_BASE = import.meta.env.VITE_INGESTION_URL || 'http://localhost:8093';

export async function fetchCurationOverrides() {
    const res = await fetch(`${INGESTION_BASE}/api/v1/ingestion/curation/overrides`);
    if (!res.ok) throw new Error(`Failed to fetch overrides: ${res.status}`);
    return res.json();
}

export async function createCurationOverride(override) {
    const res = await fetch(`${INGESTION_BASE}/api/v1/ingestion/curation/overrides`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(override),
    });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || `Failed to create override: ${res.status}`);
    }
    return res.json();
}

export async function deleteCurationOverride(id) {
    const res = await fetch(`${INGESTION_BASE}/api/v1/ingestion/curation/overrides/${id}`, {
        method: 'DELETE',
    });
    if (!res.ok) throw new Error(`Failed to delete override: ${res.status}`);
}

export const CURATION_ACTIONS = ['boost', 'demote', 'exclude', 'pin_as_portal', 'rename_topic'];
export const MAX_BUDGET = 50;
