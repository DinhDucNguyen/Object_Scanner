# Wave 3: Visual Analytics Dashboard

> **Duration**: 3 ngày  
> **Dependencies**: Wave 1 complete (need learning data)  
> **Focus**: Data visualization với MPAndroidChart

---

## Task 3.1: MPAndroidChart Integration (0.5 ngày)
- Add dependency: `com.github.PhilJay:MPAndroidChart:v3.1.0`
- Setup AnalyticsFragment + ViewModel
- Configure chart styling (Material 3 colors)

## Task 3.2: Progress Line Chart (0.5 ngày)
- Fetch learning history: `GET /api/learning/progress`
- LineChart: X=dates (30 days), Y=cumulative words learned
- Gradient fill, circular markers, interactive tap

## Task 3.3: Mastery & Category Charts (1 ngày)
- **PieChart**: Mastery distribution (New/Learning/Mastered)
  - Custom colors, center text, legend
- **BarChart**: Words per category (horizontal bars)
  - Sort descending, color-coded

## Task 3.4: Activity Heatmap (1 ngày)
- GitHub-style calendar (7x5 grid = 35 days)
- Color intensity: 0 words (gray) → 10+ (dark green)
- Tap cell → popup "March 10: 5 words"
- Scrollable for past months

---

## Verification
```bash
✅ All charts render without errors
✅ Line chart shows growth trend
✅ Pie chart percentages = 100%
✅ Bar chart sorted correctly
✅ Heatmap dates accurate
✅ Charts animate smoothly
✅ Performance good với 500+ data points
```
