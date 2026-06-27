package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Document
import com.example.data.DocumentRepository
import com.example.data.SemanticSearchResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchState {
    object Idle : SearchState
    object Loading : SearchState
    data class Success(val response: SemanticSearchResponse) : SearchState
    data class Error(val message: String) : SearchState
}

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    val allDocuments: StateFlow<List<Document>>

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _apiKeyOverride = MutableStateFlow("")
    val apiKeyOverride: StateFlow<String> = _apiKeyOverride.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DocumentRepository(database.documentDao())
        allDocuments = repository.allDocuments.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateApiKeyOverride(key: String) {
        _apiKeyOverride.value = key
    }

    fun performSearch() {
        val query = _searchQuery.value
        if (query.isBlank()) return

        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                val apiKey = _apiKeyOverride.value.takeIf { it.isNotBlank() }
                val result = repository.searchDocuments(query, apiKey)
                _searchState.value = SearchState.Success(result)
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(e.localizedMessage ?: "An unexpected error occurred.")
            }
        }
    }

    fun clearSearch() {
        _searchState.value = SearchState.Idle
        _searchQuery.value = ""
    }

    fun addDocument(title: String, fileType: String, content: String) {
        viewModelScope.launch {
            val size = (content.length * 2) / 1024 // approximation in KB
            val document = Document(
                title = title.trim(),
                fileType = fileType,
                content = content,
                sizeInKb = if (size > 0) size else 1,
                lastModified = System.currentTimeMillis()
            )
            repository.insertDocument(document)
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repository.deleteDocument(id)
        }
    }

    fun clearAllDocuments() {
        viewModelScope.launch {
            repository.deleteAllDocuments()
            _searchState.value = SearchState.Idle
        }
    }

    fun importDemoDocuments() {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                // Delete existing first to avoid duplication or cluttered UI
                repository.deleteAllDocuments()

                val demoDocs = listOf(
                    Document(
                        title = "Company Q3 Financials",
                        fileType = "XLSX",
                        content = """
                            Quarterly financial report for Q3.
                            
                            REVENUE DATA:
                            - Subscription Revenue: ${'$'}3,850,000 (up 18% YoY)
                            - Professional Services: ${'$'}890,000 (up 4% YoY)
                            - Enterprise License Sales: ${'$'}500,000 (down 5% YoY)
                            - Total Revenue: ${'$'}5,240,000 (up 12% YoY)
                            
                            EXPENSES BREAKDOWN:
                            - Cost of Goods Sold (COGS): ${'$'}1,850,000
                            - Marketing & Brand Campaigns: ${'$'}850,000
                            - Research & Development (R&D): ${'$'}1,200,000
                            - Sales and G&A: ${'$'}600,000
                            - Total Operating Expenses: ${'$'}2,650,000
                            
                            PROFIT & BALANCE:
                            - Net Income / Profit: ${'$'}740,000 (up 8% YoY)
                            - Cash and Cash Equivalents: ${'$'}3,100,000
                            - Outstanding Debts: None
                            
                            OPERATIONAL SUMMARY:
                            Our recurring subscription model growth exceeded all quarterly targets, offsetting higher marketing spend on the summer campaign. Profit margins remain healthy at 14.12%.
                        """.trimIndent(),
                        sizeInKb = 3,
                        summary = "Financial report detailing $5.24M Q3 revenue and $740k profit."
                    ),
                    Document(
                        title = "Summer Brand Strategy",
                        fileType = "DOCX",
                        content = """
                            Summer Product Launch & Brand Marketing Strategy.
                            
                            TARGET AUDIENCE PROFILE:
                            - Tech-savvy professionals aged 25-45.
                            - Mid-to-senior level managers seeking workflow efficiency.
                            
                            MARKETING BUDGET ALLOCATION:
                            - Social Media Ads (LinkedIn, YouTube): ${'$'}400,000. Focuses on high-impact, short productivity video formats.
                            - Search Engine Marketing (Google Ads): ${'$'}250,000. Targets intent keywords like "semantic workspace", "ai document search".
                            - Influencer Partnerships: ${'$'}200,000. Working with 15 key content creators in the productivity and developer space.
                            - Total Budget: ${'$'}850,000
                            
                            CORE KEY MESSAGING:
                            "Streamline Your Workspace with DocuMind AI - Search less, accomplish more."
                            
                            SUCCESS CRITERIA & METRICS:
                            - Increase free tier signups by 50,000 during the campaign period.
                            - Achieve a Click-Through Rate (CTR) of >3.5% on paid ads.
                            - Target Customer Acquisition Cost (CAC): Under ${'$'}18 per user.
                        """.trimIndent(),
                        sizeInKb = 2,
                        summary = "Marketing strategy plan targeting tech pros with a $850k budget."
                    ),
                    Document(
                        title = "Employee Operational Handbook",
                        fileType = "PDF",
                        content = """
                            Company Employee Handbook and Operational Policies.
                            
                            WELCOME & REMOTE CULTURE:
                            We strive for extreme operational excellence and maintain a remote-first, high-trust collaboration culture.
                            
                            WORKING POLICIES:
                            - Core Working Hours: 10:00 AM to 4:00 PM EST. Flexible schedules outside of these core hours are supported.
                            - Paid Time Off (PTO): Full-time employees receive 25 days of paid annual leave, plus 10 standard federal holidays.
                            - Wellness & Stipends: We cover 90% of medical, dental, and vision insurance premiums. We also provide a monthly ${'$'}150 home office / wellness stipend.
                            
                            HARDWARE & EQUIPMENT PROVISION:
                            Upon hire, employees receive the following standard hardware setup:
                            - Apple MacBook Pro (16-inch M3 Pro, 32GB RAM, 1TB SSD)
                            - Dell 27-inch 4K External Monitor
                            - Sony Noise-Canceling Wireless Headphones
                            - Apple Magic Keyboard & Mouse
                            
                            SECURITY & CONFIDENTIALITY:
                            All internal company data, customer files, and document databases are confidential. They must be accessed securely using the corporate VPN. Sharing company documents externally is strictly prohibited.
                        """.trimIndent(),
                        sizeInKb = 4,
                        summary = "Employee handbook describing PTO, benefits, hardware, and VPN security rules."
                    ),
                    Document(
                        title = "Project Alpha Sprint Review",
                        fileType = "TXT",
                        content = """
                            Sprint 4 Review Meeting Notes - Project Alpha.
                            Date: June 15, 2026.
                            
                            ATTENDEES:
                            - Sarah (Product Manager)
                            - John (Lead Engineer)
                            - Elena (UI/UX Designer)
                            - Marcus (Marketing Manager)
                            
                            TOPIC DISCUSSION:
                            - UI Design: Elena presented the finalized search dashboard mockups. Feedback was extremely positive; the typography layout maintains elegant, generous negative space.
                            - Engineering: John completed the local SQLite/Room database migration. The search engine is fully integrated with the Gemini REST API.
                            - Marketing: Marcus is coordinating the launch newsletter. The email blast is scheduled to release on July 1.
                            
                            IMMEDIATE ACTION ITEMS:
                            1. John: Optimize OkHttp network client timeouts to 60 seconds. (Due Date: June 18)
                            2. Elena: Deliver the high-resolution adaptive app icons and final visual assets. (Due Date: June 20)
                            3. Sarah: Schedule a sync with executive sponsors to finalize the Q3 budget increases. (Due Date: June 22)
                        """.trimIndent(),
                        sizeInKb = 1,
                        summary = "Sprint 4 notes listing designs, John's database work, and July 1 launch plan."
                    )
                )

                demoDocs.forEach { repository.insertDocument(it) }
            } finally {
                _isImporting.value = false
            }
        }
    }
}
