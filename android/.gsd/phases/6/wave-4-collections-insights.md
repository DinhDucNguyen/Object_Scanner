# Wave 4: Smart Collections & Insights

> **Duration**: 2 ngày  
> **Dependencies**: Wave 3 complete  
> **Focus**: Advanced organization & personalized recommendations

---

## Task 4.1: Enhanced Collections UI (1 ngày)

### Auto-Create Collections
- Fetch categories from backend
- Auto-generate collection per category (Animals, Food, Objects...)
- CollectionDetailFragment với RecyclerView

### Filtering & Sorting
**Filters:**
- By mastery: New / Learning / Mastered
- By difficulty: Easy (90%+) / Medium (70-89%) / Hard (<70%)
- By date: Recent / Oldest

**Sort options:**
- Alphabetical A-Z
- Progress (mastery level)
- Due date (soonest first)

### Bulk Actions
- "Practice all 15 words in this collection"
- "Mark all as reviewed"
- "Export to CSV"

### Collection Stats Card
```
📂 Animals Collection
━━━━━━━━━━━━━━━━━━━
15 words • 60% mastered
8 due today • Last studied: 2h ago
```

---

## Task 4.2: Insights & Recommendations (1 ngày)

### Insight Calculations
Process learning data to extract:
1. **Best Learning Time**
   - Analyze review timestamps
   - Find peak performance hours
   - "You learn best 6-9 PM"

2. **Weakest Category**
   - Calculate accuracy per category
   - Identify lowest performing
   - "Animals: 45% accuracy"

3. **Statistics**
   - Average reviews per day
   - Current streak
   - Longest streak (record)
   - Words due tomorrow

4. **Predictions**
   - Estimated mastery date for new words
   - "You'll master 'Apple' in 7 days"

### Recommendation Engine
Smart suggestions based on patterns:
- "Review Animals (your weakest)"
- "Practice 8 words due tomorrow"
- "Try evening sessions (your best time)"
- "You're 2 words away from daily goal!"

### Daily Goal Tracker
```
Daily Goal: 7/10 words ⭐⭐⭐
Progress: ████████░░ 70%

3 more to reach goal 🎯
```

---

## Verification
```bash
✅ Collections auto-created for all categories
✅ Filters work correctly (3 filter types)
✅ Sort options functional (3 sort types)
✅ Stats calculations accurate
✅ Insights update daily
✅ Recommendations relevant & actionable
✅ Daily goal progress updates real-time
```
