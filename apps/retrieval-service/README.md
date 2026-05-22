# retrieval-service

Vector search / RAG retrieval. Reads indexed chunks from **Qdrant** (written by ingestion-service).

```powershell
# hybrid: geostat infra tunnel first
cd apps/retrieval-service
..\..\apps\backend\gradlew.bat bootRun
# POST http://localhost:8092/api/v1/retrieval/search
# {"text":"query","locale":"ka","maxChunks":5,"corpusName":"geostat-portal"}
```

Contracts: `libs/platform-contracts` · Embeddings: `libs/embedding-adapters` (`hash-v1` | `gemini` | `ollama`).
