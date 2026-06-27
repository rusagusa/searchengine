package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.IOException

class DocumentRepository(private val documentDao: DocumentDao) {

    val allDocuments: Flow<List<Document>> = documentDao.getAllDocuments()

    suspend fun insertDocument(document: Document): Long = withContext(Dispatchers.IO) {
        documentDao.insertDocument(document)
    }

    suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        documentDao.deleteDocumentById(id)
    }

    suspend fun deleteAllDocuments() = withContext(Dispatchers.IO) {
        documentDao.deleteAllDocuments()
    }

    suspend fun getDocumentById(id: Long): Document? = withContext(Dispatchers.IO) {
        documentDao.getDocumentById(id)
    }

    /**
     * Performs a semantic search across all indexed documents using Gemini API.
     */
    suspend fun searchDocuments(
        query: String,
        overrideApiKey: String? = null
    ): SemanticSearchResponse = withContext(Dispatchers.IO) {
        // 1. Get the API Key
        val apiKey = if (!overrideApiKey.isNullOrBlank()) {
            overrideApiKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key is not configured. Please add your GEMINI_API_KEY in the Secrets panel or enter it manually.")
        }

        // 2. Fetch all documents from Room
        // For a Flow, collect the first emission synchronously in this coroutine
        val allDocsFlow = documentDao.getAllDocuments()
        val documents = allDocsFlow.firstOrNull() ?: emptyList()

        if (documents.isEmpty()) {
            return@withContext SemanticSearchResponse(
                answer = "No documents found in the database. Please add or import some documents first!",
                results = emptyList()
            )
        }

        // 3. Build the context for the prompt
        val documentsContext = StringBuilder()
        documents.forEach { doc ->
            documentsContext.append("--- DOCUMENT START ---\n")
            documentsContext.append("ID: ${doc.id}\n")
            documentsContext.append("TITLE: ${doc.title}\n")
            documentsContext.append("FILE TYPE: ${doc.fileType}\n")
            documentsContext.append("SIZE: ${doc.sizeInKb} KB\n")
            // Send a reasonable snippet of the content (e.g., first 2000 chars) to prevent token overflow
            val contentSnippet = if (doc.content.length > 2000) {
                doc.content.substring(0, 2000) + "... [Truncated due to size]"
            } else {
                doc.content
            }
            documentsContext.append("CONTENT:\n$contentSnippet\n")
            documentsContext.append("--- DOCUMENT END ---\n\n")
        }

        val prompt = """
            You are an advanced AI semantic search engine specializing in natural language document processing.
            You have access to a database of documents containing PDFs, spreadsheets, Word documents, and text files.
            
            USER'S SEARCH QUERY:
            "$query"
            
            INDEXED DOCUMENTS LIST:
            $documentsContext
            
            YOUR TASK:
            1. Search and analyze all the indexed documents above (titles, types, and content) to find information relevant to the user's query.
            2. Formulate a comprehensive, direct, and conversational 'answer' that answers the query based on the facts in the matching documents. 
               - If multiple documents are relevant, synthesize their information together.
               - Reference specific documents by their titles (e.g., "...as mentioned in 'Q3 Reports.xlsx'").
               - Do not make up facts. Only use info in the provided documents.
               - If the query cannot be answered using the documents, write a friendly message explaining that.
            3. Generate a ranked list of relevant documents ('results') with:
               - 'id': The exact document ID.
               - 'score': A relevance score from 0 to 100 (where 100 is highly relevant and 0 is completely irrelevant). Only include documents with a score > 15.
               - 'reason': A short, 1-2 sentence explanation of why this document is relevant, citing specific parts/data matching the query.
            
            OUTPUT FORMAT:
            You MUST return your response strictly as a JSON object matching this schema. Do not write any other markdown code or explanation outside of this JSON:
            {
              "answer": "Your synthesized direct answer goes here...",
              "results": [
                {
                  "id": 1,
                  "score": 95,
                  "reason": "This document contains..."
                }
              ]
            }
        """.trimIndent()

        // 4. Call the Gemini REST API
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f // low temperature for precise factual extraction
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a professional enterprise document semantic search model. You output strict JSON responses only.")))
        )

        try {
            val response = RetrofitClient.service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IOException("Received empty response from Gemini API.")

            // 5. Parse the Response
            val sanitizedJson = sanitizeJson(rawText)
            
            val adapter = RetrofitClient.moshi.adapter(SemanticSearchResponse::class.java)
            val parsedResponse = adapter.fromJson(sanitizedJson)
                ?: throw IOException("Failed to parse semantic search JSON.")

            return@withContext parsedResponse

        } catch (e: Exception) {
            Log.e("DocumentRepository", "Semantic search error", e)
            throw e
        }
    }

    /**
     * Sanitizes markdown-wrapped JSON text that might be returned by Gemini.
     */
    private fun sanitizeJson(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```")) {
            // Remove starting block (e.g., ```json or ```)
            val firstLineEnd = clean.indexOf("\n")
            if (firstLineEnd != -1) {
                clean = clean.substring(firstLineEnd).trim()
            }
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3).trim()
        }
        return clean
    }
}
