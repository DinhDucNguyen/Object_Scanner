# Wave 1: Multi-Modal Review System

> **Duration**: 4 ngày  
> **Dependencies**: None (all backend APIs exist)  
> **Focus**: Build 4 review modes (Flashcard, Quiz, Typing, Image Matching)

---

## Objectives

Transform basic review system thành **multi-modal learning platform** với 4 learning styles:
- **Visual learners**: Flashcard swipe interface
- **Active recall**: Multiple choice quiz
- **Kinesthetic**: Typing & listening tests
- **Associative**: Image-word matching game

---

## Task 1.1: Flashcard Mode (1 ngày)

### Specification

**User Story**: "Tôi muốn review nhanh bằng cách swipe flashcards như Tinder"

**Requirements:**
- ViewPager2 với horizontal swipe
- Swipe right (→) = "I know this word"
- Swipe left (←) = "I don't know"
- Swipe up (↑) = "Mark as difficult"
- Card flip animation: Front (English) → Back (Vietnamese + IPA + Example)
- Progress indicator: "3/10 cards"
- Auto-submit khi hoàn thành 10 cards
- Update SM-2 intervals based on results

**UI Design:**
```
┌────────────────────────────┐
│  Progress: 3/10            │
│                            │
│   ┌──────────────────┐     │
│   │                  │     │
│   │      APPLE       │     │ <- Card
│   │                  │     │
│   │   [Flip to see]  │     │
│   │                  │     │
│   └──────────────────┘     │
│                            │
│  ← Don't know   Know it →  │
│       ↑ Difficult          │
└────────────────────────────┘
```

### Implementation Plan

**Step 1: Create Fragment & Layout (30 min)**
```kotlin
// FlashcardFragment.kt
class FlashcardFragment : Fragment() {
    private lateinit var binding: FragmentFlashcardBinding
    private val viewModel: FlashcardViewModel by viewModels()
    
    // ViewPager2 setup
    private lateinit var flashcardAdapter: FlashcardPagerAdapter
}

// fragment_flashcard.xml
<ViewPager2
    android:id="@+id/viewPagerFlashcards"
    android:layout_width="match_parent"
    android:layout_height="0dp" />
```

**Step 2: ViewPager Adapter (1 giờ)**
```kotlin
// FlashcardPagerAdapter.kt
class FlashcardPagerAdapter(
    private val cards: List<LearningItem>
) : RecyclerView.Adapter<FlashcardViewHolder>() {
    
    override fun onBindViewHolder(holder: FlashcardViewHolder, position: Int) {
        val card = cards[position]
        holder.bind(card)
        
        // Card flip animation
        holder.itemView.setOnClickListener {
            holder.flipCard()
        }
    }
}

class FlashcardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private var isFlipped = false
    
    fun flipCard() {
        val animator = AnimatorInflater.loadAnimator(
            itemView.context, 
            R.animator.card_flip
        )
        animator.setTarget(itemView)
        animator.start()
        isFlipped = !isFlipped
        // Toggle front/back visibility
    }
}
```

**Step 3: Swipe Gesture Detection (1.5 giờ)**
```kotlin
// SwipeGestureDetector.kt
class SwipeGestureDetector(
    private val onSwipeRight: () -> Unit,
    private val onSwipeLeft: () -> Unit,
    private val onSwipeUp: () -> Unit
) : GestureDetector.SimpleOnGestureListener() {
    
    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val deltaX = e2.x - e1.x
        val deltaY = e2.y - e1.y
        
        when {
            abs(deltaX) > abs(deltaY) && abs(deltaX) > SWIPE_THRESHOLD -> {
                if (deltaX > 0) onSwipeRight() else onSwipeLeft()
            }
            deltaY < -SWIPE_THRESHOLD -> onSwipeUp()
        }
        return true
    }
}

// In FlashcardFragment:
val gestureDetector = GestureDetector(requireContext(), SwipeGestureDetector(
    onSwipeRight = { viewModel.markKnown(currentCard) },
    onSwipeLeft = { viewModel.markUnknown(currentCard) },
    onSwipeUp = { viewModel.markDifficult(currentCard) }
))

viewPager.setOnTouchListener { v, event -> 
    gestureDetector.onTouchEvent(event)
}
```

**Step 4: ViewModel & API Integration (2 giờ)**
```kotlin
// FlashcardViewModel.kt
class FlashcardViewModel(private val repository: AppRepository) : ViewModel() {
    
    private val _cards = MutableLiveData<List<LearningItem>>()
    val cards: LiveData<List<LearningItem>> = _cards
    
    private val _currentIndex = MutableLiveData(0)
    val currentIndex: LiveData<Int> = _currentIndex
    
    private val results = mutableMapOf<Int, ReviewResult>()
    
    fun loadDueCards() {
        viewModelScope.launch {
            val dueItems = repository.getDueWords() // GET /api/learning/due
            _cards.value = dueItems
        }
    }
    
    fun markKnown(card: LearningItem) {
        results[card.id] = ReviewResult(quality = 5) // Perfect recall
        moveToNext()
    }
    
    fun markUnknown(card: LearningItem) {
        results[card.id] = ReviewResult(quality = 0) // Complete blackout
        moveToNext()
    }
    
    fun markDifficult(card: LearningItem) {
        results[card.id] = ReviewResult(quality = 2) // Difficult
        moveToNext()
    }
    
    private fun moveToNext() {
        val current = _currentIndex.value ?: 0
        if (current + 1 >= (_cards.value?.size ?: 0)) {
            submitResults()
        } else {
            _currentIndex.value = current + 1
        }
    }
    
    private fun submitResults() {
        viewModelScope.launch {
            results.forEach { (id, result) ->
                repository.submitReview(id, result) // POST /api/learning/{id}/review
            }
            // Show completion dialog
        }
    }
}
```

**Step 5: Animations (1 giờ)**
```xml
<!-- res/animator/card_flip.xml -->
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <objectAnimator
        android:propertyName="rotationY"
        android:duration="300"
        android:valueFrom="0"
        android:valueTo="180" />
</set>
```

**Step 6: Testing (1 giờ)**
- [ ] Test swipe gestures on 3+ devices
- [ ] Verify card flip animation smooth
- [ ] Check progress indicator updates
- [ ] Confirm API calls with network inspector
- [ ] Test với 1, 10, 50 cards

### Verification Criteria

```bash
# Manual tests
✅ Swipe right → card moves, marked as known
✅ Swipe left → card moves, marked as unknown
✅ Swipe up → card marked difficult, shows indicator
✅ Tap card → flips to show back (translation)
✅ Progress shows "3/10" accurately
✅ After 10 cards → completion dialog shows
✅ Backend receives correct SM-2 quality scores
```

---

## Task 1.2: Multiple Choice Quiz (1 ngày)

### Specification

**User Story**: "Tôi muốn test vocabulary với quiz 4 đáp án để challenge bản thân"

**Requirements:**
- Show Vietnamese word → pick correct English translation
- Generate 4 options: 1 correct + 3 random distractors
- Optional timer: 10 seconds per question (can disable)
- Highlight correct answer (green) và wrong answer (red)
- Score tracking: "8/10 correct (80%)"
- Option to retry incorrect words immediately

### Implementation Plan

**Step 1: QuizFragment & Layout (30 min)**
```kotlin
// QuizFragment.kt
class QuizFragment : Fragment() {
    private lateinit var binding: FragmentQuizBinding
    private val viewModel: QuizViewModel by viewModels()
}

// fragment_quiz.xml
<TextView
    android:id="@+id/tvQuestion"
    android:text="Vietnamese word" />

<RadioGroup android:id="@+id/radioGroupOptions">
    <RadioButton android:id="@+id/option1" />
    <RadioButton android:id="@+id/option2" />
    <RadioButton android:id="@+id/option3" />
    <RadioButton android:id="@+id/option4" />
</RadioGroup>

<Button
    android:id="@+id/btnSubmit"
    android:text="Submit Answer" />

<TextView
    android:id="@+id/tvTimer"
    android:text="⏱️ 10s" />
```

**Step 2: Option Generation Logic (1.5 giờ)**
```kotlin
// QuizViewModel.kt
data class QuizQuestion(
    val questionWord: String, // Vietnamese
    val correctAnswer: String, // English
    val options: List<String>, // 4 options shuffled
    val correctIndex: Int
)

class QuizViewModel(private val repository: AppRepository) : ViewModel() {
    
    private val _currentQuestion = MutableLiveData<QuizQuestion>()
    val currentQuestion: LiveData<QuizQuestion> = _currentQuestion
    
    private val _score = MutableLiveData(0)
    val score: LiveData<Int> = _score
    
    private var questions = mutableListOf<QuizQuestion>()
    private var currentIndex = 0
    
    fun loadQuiz() {
        viewModelScope.launch {
            val dueWords = repository.getDueWords()
            val allWords = repository.getAllWords() // For distractors
            
            questions = dueWords.map { word ->
                generateQuestion(word, allWords)
            }.toMutableList()
            
            _currentQuestion.value = questions[0]
        }
    }
    
    private fun generateQuestion(
        word: LearningItem,
        allWords: List<LearningItem>
    ): QuizQuestion {
        // Get 3 random wrong answers (distractors)
        val distractors = allWords
            .filter { it.id != word.id }
            .shuffled()
            .take(3)
            .map { it.englishWord }
        
        // Combine with correct answer and shuffle
        val options = (distractors + word.englishWord).shuffled()
        val correctIndex = options.indexOf(word.englishWord)
        
        return QuizQuestion(
            questionWord = word.vietnameseWord,
            correctAnswer = word.englishWord,
            options = options,
            correctIndex = correctIndex
        )
    }
    
    fun submitAnswer(selectedIndex: Int) {
        val question = _currentQuestion.value ?: return
        val isCorrect = (selectedIndex == question.correctIndex)
        
        if (isCorrect) {
            _score.value = (_score.value ?: 0) + 1
        }
        
        // Record result
        results[currentIndex] = ReviewResult(
            quality = if (isCorrect) 5 else 2
        )
        
        moveToNext()
    }
}
```

**Step 3: Timer Implementation (1 giờ)**
```kotlin
// In QuizViewModel
private var timer: CountDownTimer? = null

fun startTimer() {
    timer?.cancel()
    timer = object : CountDownTimer(10000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            _timerValue.value = (millisUntilFinished / 1000).toInt()
        }
        
        override fun onFinish() {
            // Auto-submit as incorrect
            submitAnswer(-1) // No answer selected
        }
    }.start()
}

override fun onCleared() {
    super.onCleared()
    timer?.cancel()
}
```

**Step 4: Answer Highlighting (1 giờ)**
```kotlin
// In QuizFragment
viewModel.answerResult.observe(viewLifecycleOwner) { result ->
    when {
        result.isCorrect -> {
            // Highlight selected option green
            radioButtons[result.selectedIndex].setBackgroundColor(Color.GREEN)
        }
        else -> {
            // Highlight selected red, show correct in green
            radioButtons[result.selectedIndex].setBackgroundColor(Color.RED)
            radioButtons[result.correctIndex].setBackgroundColor(Color.GREEN)
        }
    }
    
    // Disable further selection
    radioGroup.isEnabled = false
    
    // Auto-move to next after 2 seconds
    Handler(Looper.getMainLooper()).postDelayed({
        moveToNextQuestion()
    }, 2000)
}
```

**Step 5: Score Summary (1 giờ)**
```kotlin
// QuizResultDialogFragment.kt
class QuizResultDialogFragment : DialogFragment() {
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val score = arguments?.getInt("score") ?: 0
        val total = arguments?.getInt("total") ?: 0
        val percentage = (score * 100) / total
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quiz Complete! 🎉")
            .setMessage("""
                Score: $score/$total ($percentage%)
                
                ${getEncouragementMessage(percentage)}
            """.trimIndent())
            .setPositiveButton("Review Incorrect") { _, _ ->
                // Filter incorrect words and show again
            }
            .setNegativeButton("Finish") { _, _ ->
                dismiss()
            }
            .create()
    }
    
    private fun getEncouragementMessage(percentage: Int) = when {
        percentage >= 90 -> "Amazing! You're a vocabulary master! 🏆"
        percentage >= 70 -> "Great job! Keep it up! ⭐"
        percentage >= 50 -> "Good effort! Practice makes perfect! 👍"
        else -> "Don't give up! Review and try again! 💪"
    }
}
```

### Verification Criteria

```bash
✅ Question shows Vietnamese word correctly
✅ 4 options displayed, 1 correct + 3 plausible distractors
✅ Timer counts down from 10 → 0
✅ Selecting answer highlights green/red appropriately
✅ Score updates correctly (8/10)
✅ Completion dialog shows percentage
✅ "Review Incorrect" filters wrong answers
```

---

## Task 1.3: Typing & Listening Tests (1 ngày)

### Implementation Summary

**Typing Test:**
- Show Vietnamese → EditText for typing English
- Use Levenshtein distance to allow 1-2 character typos
- Green checkmark if correct, red X if wrong
- Hints: "Show first letter", "Show word length"

**Listening Test:**
- Auto-play TTS audio
- EditText for typing what user hears
- Replay button (slow speed option)
- Speech-to-text comparison

(Detailed implementation similar to above tasks)

---

## Task 1.4: Image Matching Game (1 ngày)

### Implementation Summary

**Image Matching:**
- 3x2 grid (6 cards): 3 images + 3 English words
- Flip animation on tap
- Match detection: image + corresponding word
- Fade out matched pairs
- Timer challenge: complete in < 60 seconds
- Shuffle on each round

(Detailed implementation plan available upon request)

---

## Wave 1 Completion Checklist

### Functional Requirements
- [ ] Flashcard mode: Swipe gestures working
- [ ] Quiz mode: 4 options generated correctly
- [ ] Typing test: Typo tolerance implemented
- [ ] Listening test: Audio playback functional
- [ ] Image matching: Pairing logic correct
- [ ] All modes update SM-2 intervals

### Quality Requirements
- [ ] No crashes during any review mode
- [ ] Smooth animations (60 FPS)
- [ ] API calls handle errors gracefully
- [ ] UI responsive on different screen sizes
- [ ] Back button behavior correct

### Testing Checklist
- [ ] Test với 1 word
- [ ] Test với 10 words
- [ ] Test với 50+ words
- [ ] Test trên 3 devices (low/mid/high-end)
- [ ] Test với slow network (3G simulation)
- [ ] Test offline behavior (show error)

---

## Next: Wave 2

After completing Wave 1, proceed to **Wave 2: Pronunciation Practice** with speech recognition integration.
