import { useState, useEffect } from 'react';
import { Plus, Trash2, AlertCircle, CheckCircle } from 'lucide-react';
import {
    fetchCurationOverrides,
    createCurationOverride,
    deleteCurationOverride,
    CURATION_ACTIONS,
    MAX_BUDGET,
} from '../../services/curationApi.js';

export default function CurationAdmin() {
    const [overrides, setOverrides] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [showForm, setShowForm] = useState(false);
    const [formData, setFormData] = useState({
        url: '',
        action: 'boost',
        target: '',
        reason: '',
        createdBy: 'admin',
    });

    useEffect(() => {
        loadOverrides();
    }, []);

    async function loadOverrides() {
        try {
            setLoading(true);
            const data = await fetchCurationOverrides();
            setOverrides(data);
            setError(null);
        } catch (e) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    }

    async function handleCreate(e) {
        e.preventDefault();
        if (overrides.length >= MAX_BUDGET) {
            setError(`Budget exceeded: max ${MAX_BUDGET} overrides`);
            return;
        }
        try {
            await createCurationOverride(formData);
            setShowForm(false);
            setFormData({ url: '', action: 'boost', target: '', reason: '', createdBy: 'admin' });
            loadOverrides();
        } catch (e) {
            setError(e.message);
        }
    }

    async function handleDelete(id) {
        if (!confirm('Delete this override?')) return;
        try {
            await deleteCurationOverride(id);
            loadOverrides();
        } catch (e) {
            setError(e.message);
        }
    }

    const budgetPercent = (overrides.length / MAX_BUDGET) * 100;

    return (
        <div className="curation-admin">
            <div className="curation-header">
                <h2>Curation Overrides</h2>
                <div className="budget-gauge">
                    <div
                        className="budget-fill"
                        style={{
                            width: `${budgetPercent}%`,
                            backgroundColor: budgetPercent > 80 ? '#ef4444' : '#22c55e',
                        }}
                    />
                    <span>{overrides.length} / {MAX_BUDGET}</span>
                </div>
            </div>

            {error && (
                <div className="error-banner">
                    <AlertCircle size={16} />
                    <span>{error}</span>
                    <button onClick={() => setError(null)}>×</button>
                </div>
            )}

            <div className="actions-bar">
                <button className="btn-primary" onClick={() => setShowForm(!showForm)}>
                    <Plus size={16} /> Add Override
                </button>
                <button className="btn-secondary" onClick={loadOverrides}>
                    Refresh
                </button>
            </div>

            {showForm && (
                <form className="override-form" onSubmit={handleCreate}>
                    <div className="form-group">
                        <label>URL</label>
                        <input
                            type="url"
                            required
                            placeholder="https://geostat.ge/..."
                            value={formData.url}
                            onChange={(e) => setFormData({ ...formData, url: e.target.value })}
                        />
                    </div>
                    <div className="form-group">
                        <label>Action</label>
                        <select
                            value={formData.action}
                            onChange={(e) => setFormData({ ...formData, action: e.target.value })}
                        >
                            {CURATION_ACTIONS.map((a) => (
                                <option key={a} value={a}>{a}</option>
                            ))}
                        </select>
                    </div>
                    <div className="form-group">
                        <label>Target (topic/query)</label>
                        <input
                            type="text"
                            placeholder="e.g., inflation, gdp"
                            value={formData.target}
                            onChange={(e) => setFormData({ ...formData, target: e.target.value })}
                        />
                    </div>
                    <div className="form-group">
                        <label>Reason (mandatory)</label>
                        <textarea
                            required
                            placeholder="Why is this override needed?"
                            value={formData.reason}
                            onChange={(e) => setFormData({ ...formData, reason: e.target.value })}
                        />
                    </div>
                    <div className="form-actions">
                        <button type="submit" className="btn-primary">Create</button>
                        <button type="button" className="btn-secondary" onClick={() => setShowForm(false)}>
                            Cancel
                        </button>
                    </div>
                </form>
            )}

            {loading ? (
                <div className="loading">Loading...</div>
            ) : (
                <table className="overrides-table">
                    <thead>
                        <tr>
                            <th>URL</th>
                            <th>Action</th>
                            <th>Target</th>
                            <th>Reason</th>
                            <th>Expires</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>
                        {overrides.length === 0 ? (
                            <tr>
                                <td colSpan={6} className="empty">No overrides</td>
                            </tr>
                        ) : (
                            overrides.map((o) => (
                                <tr key={o.id}>
                                    <td className="url-cell" title={o.url}>
                                        {o.url?.substring(0, 40)}...
                                    </td>
                                    <td>
                                        <span className={`action-badge action-${o.action}`}>
                                            {o.action}
                                        </span>
                                    </td>
                                    <td>{o.target || '—'}</td>
                                    <td className="reason-cell">{o.reason}</td>
                                    <td>{o.expiresAt ? new Date(o.expiresAt).toLocaleDateString() : '—'}</td>
                                    <td>
                                        <button
                                            className="btn-icon btn-danger"
                                            onClick={() => handleDelete(o.id)}
                                        >
                                            <Trash2 size={16} />
                                        </button>
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            )}
        </div>
    );
}
