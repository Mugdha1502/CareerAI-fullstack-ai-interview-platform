// Demo content for Aptitude, Coding, SQL, System Design, Voice and Daily Challenge.
// TODO backend: replace with responses from the CareerAI practice API.

/* =====================================================
   APTITUDE
   Numeric topics are generated so any question count works.
   Static topics use a small fixed bank.
===================================================== */

export const aptitudeTopics = [
  "Percentages",
  "Profit & Loss",
  "Ratio",
  "Time & Work",
  "Probability",
  "Logical Reasoning",
  "Verbal Ability",
  "Data Interpretation"
];

const formatNumber = (value) => Number(value).toLocaleString("en-IN");
const rupees = (value) => `₹${formatNumber(value)}`;
const pick = (list) => list[Math.floor(Math.random() * list.length)];
const gcd = (a, b) => (b === 0 ? a : gcd(b, a % b));

// First four unique values, always including the correct answer first.
const uniqueOptions = (values) => [...new Set(values)].slice(0, 4);

function fraction(numerator, denominator) {
  const divisor = gcd(numerator, denominator);
  return `${numerator / divisor}/${denominator / divisor}`;
}

// Builds 4 unique options around a correct value.
function numericOptions(correct, offsets, format = (v) => String(v)) {
  const values = new Set([correct]);

  for (const offset of offsets) {
    const candidate = correct + offset;
    if (candidate > 0) values.add(candidate);
    if (values.size === 4) break;
  }

  let step = 1;
  while (values.size < 4) {
    values.add(correct + step * 7);
    step += 1;
  }

  return { options: [...values].map(format), answer: format(correct) };
}

const aptitudeGenerators = {
  Percentages: (difficulty) => {
    if (difficulty === "Beginner") {
      const percent = pick([10, 15, 20, 25, 30, 40]);
      const base = pick([200, 240, 360, 400, 480, 600, 800]);
      const correct = (percent * base) / 100;
      return {
        question: `What is ${percent}% of ${formatNumber(base)}?`,
        ...numericOptions(correct, [10, -10, 20], formatNumber),
        explanation: `${percent}% of ${base} = ${base} × ${percent}/100 = ${correct}.`,
        tag: "Basic percentages"
      };
    }

    const percent = pick([10, 20, 25]);
    const price = pick([1000, 2000, 4000, 5000]);
    const up = price * (1 + percent / 100);
    const correct = up * (1 - percent / 100);
    return {
      question: `A price of ${rupees(price)} increases by ${percent}% and then decreases by ${percent}%. What is the final price?`,
      ...numericOptions(correct, [price - correct, (price - correct) * 2, -(price - correct)], rupees),
      explanation: `After a ${percent}% increase the price is ${rupees(up)}. A ${percent}% decrease on that gives ${rupees(correct)} — a net loss of ${(percent * percent) / 100}%.`,
      tag: "Successive change"
    };
  },

  "Profit & Loss": (difficulty) => {
    const cost = pick([400, 500, 800, 1200, 1500, 2000]);
    const rate = pick(difficulty === "Beginner" ? [10, 20, 25] : [12, 15, 18, 30]);
    const isLoss = difficulty === "Advanced" && Math.random() > 0.5;
    const correct = isLoss ? cost * (1 - rate / 100) : cost * (1 + rate / 100);
    return {
      question: `An item bought for ${rupees(cost)} is sold at a ${rate}% ${isLoss ? "loss" : "profit"}. What is the selling price?`,
      ...numericOptions(correct, [cost * 0.05, -cost * 0.05, cost * 0.1], rupees),
      explanation: `Selling price = cost × (1 ${isLoss ? "−" : "+"} ${rate}/100) = ${rupees(cost)} × ${isLoss ? (100 - rate) / 100 : (100 + rate) / 100} = ${rupees(correct)}.`,
      tag: isLoss ? "Loss calculation" : "Profit calculation"
    };
  },

  Ratio: () => {
    const [a, b] = pick([[2, 3], [3, 5], [4, 5], [1, 4], [5, 7]]);
    const total = (a + b) * pick([100, 200, 300, 500]);
    const correct = (total / (a + b)) * Math.max(a, b);
    return {
      question: `${rupees(total)} is divided between two people in the ratio ${a}:${b}. What is the larger share?`,
      ...numericOptions(correct, [-(total / (a + b)), total / (a + b), 100], rupees),
      explanation: `Total parts = ${a + b}. One part = ${rupees(total / (a + b))}. Larger share = ${Math.max(a, b)} × ${rupees(total / (a + b))} = ${rupees(correct)}.`,
      tag: "Dividing in a ratio"
    };
  },

  "Time & Work": () => {
    const [a, b, correct] = pick([
      [12, 6, 4], [10, 15, 6], [20, 30, 12], [6, 3, 2], [24, 12, 8],
      [18, 9, 6], [30, 20, 12], [12, 4, 3], [36, 18, 12], [20, 5, 4]
    ]);
    return {
      question: `A can complete a job in ${a} days and B can complete it in ${b} days. How many days will they take working together?`,
      ...numericOptions(correct, [1, 2, -1], (v) => `${v} days`),
      explanation: `Combined rate = 1/${a} + 1/${b} = 1/${correct} of the job per day, so together they need ${correct} days.`,
      tag: "Combined work"
    };
  },

  Probability: (difficulty) => {
    if (difficulty === "Beginner") {
      const tosses = pick([2, 3, 4]);
      const correct = fraction(2 ** tosses - 1, 2 ** tosses);
      return {
        question: `A fair coin is tossed ${tosses} times. What is the probability of getting at least one head?`,
        options: uniqueOptions([correct, fraction(1, 2 ** tosses), "1/2", fraction(tosses, 2 ** tosses + 1), "3/4"]),
        answer: correct,
        explanation: `P(no heads) = (1/2)^${tosses} = 1/${2 ** tosses}. So P(at least one head) = 1 − 1/${2 ** tosses} = ${correct}.`,
        tag: "Complement rule"
      };
    }

    const sum = pick([5, 6, 7, 8, 9]);
    const ways = 6 - Math.abs(7 - sum);
    const correct = fraction(ways, 36);
    return {
      question: `Two fair dice are rolled. What is the probability that the sum is ${sum}?`,
      options: uniqueOptions([correct, fraction(ways + 1, 36), fraction(ways - 1, 36), "1/6", "1/12", "1/9"]),
      answer: correct,
      explanation: `There are 36 equally likely outcomes and ${ways} of them give a sum of ${sum}, so the probability is ${ways}/36 = ${correct}.`,
      tag: "Dice outcomes"
    };
  }
};

const aptitudeBank = {
  "Logical Reasoning": [
    {
      question: "Find the next number in the series: 2, 6, 12, 20, 30, ?",
      options: ["40", "42", "44", "36"],
      answer: "42",
      explanation: "Differences are 4, 6, 8, 10, so the next difference is 12: 30 + 12 = 42.",
      tag: "Number series"
    },
    {
      question: "If all developers are engineers and some engineers are designers, which statement must be true?",
      options: [
        "Some developers are designers",
        "All engineers are developers",
        "All developers are engineers",
        "No developers are designers"
      ],
      answer: "All developers are engineers",
      explanation: "Only the given premise is certain. The overlap between developers and designers cannot be concluded.",
      tag: "Syllogisms"
    },
    {
      question: "In a code language, CAT is written as DBU. How is DOG written?",
      options: ["EPH", "FQI", "EOH", "DPH"],
      answer: "EPH",
      explanation: "Each letter shifts one position forward: D→E, O→P, G→H.",
      tag: "Coding–decoding"
    },
    {
      question: "Pointing to a man, Riya says, 'He is the son of my mother's only brother.' How is the man related to Riya?",
      options: ["Cousin", "Brother", "Uncle", "Nephew"],
      answer: "Cousin",
      explanation: "Her mother's only brother is Riya's uncle; his son is Riya's cousin.",
      tag: "Blood relations"
    },
    {
      question: "Which number does not belong: 3, 5, 11, 14, 17, 23?",
      options: ["14", "11", "17", "23"],
      answer: "14",
      explanation: "All other numbers are prime; 14 is not.",
      tag: "Odd one out"
    },
    {
      question: "A person walks 5 km north, then 3 km east, then 5 km south. How far are they from the start?",
      options: ["3 km", "5 km", "8 km", "13 km"],
      answer: "3 km",
      explanation: "The north and south walks cancel out, leaving a 3 km displacement to the east.",
      tag: "Directions"
    }
  ],
  "Verbal Ability": [
    {
      question: "Choose the grammatically correct sentence.",
      options: [
        "Neither of the candidates have submitted their resume.",
        "Neither of the candidates has submitted a resume.",
        "Neither of the candidate has submitted their resumes.",
        "Neither candidates has submitted a resume."
      ],
      answer: "Neither of the candidates has submitted a resume.",
      explanation: "'Neither' is singular, so it takes the singular verb 'has'.",
      tag: "Grammar"
    },
    {
      question: "Choose the synonym of 'meticulous'.",
      options: ["Careless", "Thorough", "Hasty", "Generous"],
      answer: "Thorough",
      explanation: "Meticulous means showing great attention to detail — thorough.",
      tag: "Vocabulary"
    },
    {
      question: "Choose the antonym of 'expand'.",
      options: ["Contract", "Extend", "Increase", "Spread"],
      answer: "Contract",
      explanation: "To expand is to grow larger; to contract is to become smaller.",
      tag: "Vocabulary"
    },
    {
      question: "Fill in the blank: The project was delayed ___ unexpected technical issues.",
      options: ["due to", "despite", "although", "unless"],
      answer: "due to",
      explanation: "'Due to' introduces the cause of the delay.",
      tag: "Sentence completion"
    },
    {
      question: "Identify the correctly spelt word.",
      options: ["Accomodate", "Acommodate", "Accommodate", "Acomodate"],
      answer: "Accommodate",
      explanation: "Accommodate has a double 'c' and a double 'm'.",
      tag: "Spelling"
    }
  ],
  "Data Interpretation": [
    {
      question: "Using the table, what was the total number of applications across all three months?",
      table: {
        headers: ["Month", "Applications", "Interviews"],
        rows: [["July", "120", "18"], ["August", "150", "27"], ["September", "180", "36"]]
      },
      options: ["400", "450", "480", "420"],
      answer: "450",
      explanation: "120 + 150 + 180 = 450 applications.",
      tag: "Totals"
    },
    {
      question: "Using the table, in which month was the interview rate (interviews ÷ applications) highest?",
      table: {
        headers: ["Month", "Applications", "Interviews"],
        rows: [["July", "120", "18"], ["August", "150", "27"], ["September", "180", "36"]]
      },
      options: ["July", "August", "September", "All equal"],
      answer: "September",
      explanation: "Rates are 15% (July), 18% (August) and 20% (September).",
      tag: "Rates"
    },
    {
      question: "Using the table, by what percentage did applications grow from July to September?",
      table: {
        headers: ["Month", "Applications", "Interviews"],
        rows: [["July", "120", "18"], ["August", "150", "27"], ["September", "180", "36"]]
      },
      options: ["40%", "50%", "60%", "33%"],
      answer: "50%",
      explanation: "Growth = (180 − 120) ÷ 120 = 0.5 = 50%.",
      tag: "Percentage change"
    },
    {
      question: "Using the table, what is the average monthly revenue?",
      table: {
        headers: ["Quarter month", "Revenue (₹ lakh)"],
        rows: [["April", "24"], ["May", "30"], ["June", "36"]]
      },
      options: ["₹28 lakh", "₹30 lakh", "₹32 lakh", "₹90 lakh"],
      answer: "₹30 lakh",
      explanation: "(24 + 30 + 36) ÷ 3 = 90 ÷ 3 = ₹30 lakh.",
      tag: "Averages"
    },
    {
      question: "Using the table, what share of the quarter's revenue came from June?",
      table: {
        headers: ["Quarter month", "Revenue (₹ lakh)"],
        rows: [["April", "24"], ["May", "30"], ["June", "36"]]
      },
      options: ["30%", "36%", "40%", "45%"],
      answer: "40%",
      explanation: "36 ÷ 90 = 0.4 = 40%.",
      tag: "Share of total"
    }
  ]
};

export function getAptitudeSource(topic) {
  if (aptitudeGenerators[topic]) {
    return { type: "generated", generate: aptitudeGenerators[topic] };
  }

  return { type: "bank", questions: aptitudeBank[topic] || [] };
}

export const aptitudeQuestionCounts = ["5", "10", "20"];

/* =====================================================
   CODING
===================================================== */

export const codingTopics = [
  "Arrays",
  "Strings",
  "Linked Lists",
  "Stacks",
  "Queues",
  "Trees",
  "Searching",
  "Sorting",
  "Hashing",
  "Dynamic Programming"
];

export const codingLanguages = ["Java", "Python", "JavaScript", "C++"];

// signature: what goes inside the class/function header for each language.
// fallback: the placeholder return value in the starter code.
export const codingProblems = {
  Arrays: {
    title: "Second Largest Element",
    description: "Given an array of integers, return the second largest distinct value. If it does not exist, return -1.",
    examples: [
      { input: "nums = [12, 35, 1, 10, 34, 1]", output: "34" },
      { input: "nums = [10, 10, 10]", output: "-1", explanation: "There is no second distinct value." }
    ],
    constraints: ["1 ≤ nums.length ≤ 10⁵", "-10⁹ ≤ nums[i] ≤ 10⁹", "Aim for O(n) time and O(1) extra space."],
    signature: { Java: "public int secondLargest(int[] nums)", Python: "def second_largest(nums):", JavaScript: "function secondLargest(nums)", "C++": "int secondLargest(vector<int>& nums)" },
    fallback: "-1",
    tests: [
      { input: "[12, 35, 1, 10, 34, 1]", expected: "34" },
      { input: "[5, 9]", expected: "5" },
      { input: "[10, 10, 10]", expected: "-1" }
    ]
  },
  Strings: {
    title: "Valid Palindrome",
    description: "Return true if the string reads the same forward and backward after removing non-alphanumeric characters and ignoring case.",
    examples: [
      { input: "s = \"A man, a plan, a canal: Panama\"", output: "true" },
      { input: "s = \"race a car\"", output: "false" }
    ],
    constraints: ["1 ≤ s.length ≤ 2 × 10⁵", "s contains printable ASCII characters."],
    signature: { Java: "public boolean isPalindrome(String s)", Python: "def is_palindrome(s):", JavaScript: "function isPalindrome(s)", "C++": "bool isPalindrome(string s)" },
    fallback: "false",
    tests: [
      { input: "\"A man, a plan, a canal: Panama\"", expected: "true" },
      { input: "\"race a car\"", expected: "false" },
      { input: "\" \"", expected: "true" }
    ]
  },
  "Linked Lists": {
    title: "Reverse a Linked List",
    description: "Given the head of a singly linked list, reverse the list and return the new head. A ListNode class with val and next is provided.",
    examples: [
      { input: "head = 1 → 2 → 3 → 4 → 5", output: "5 → 4 → 3 → 2 → 1" },
      { input: "head = 1 → 2", output: "2 → 1" }
    ],
    constraints: ["0 ≤ number of nodes ≤ 5000", "Solve it iteratively in O(n) time and O(1) space."],
    signature: { Java: "public ListNode reverseList(ListNode head)", Python: "def reverse_list(head):", JavaScript: "function reverseList(head)", "C++": "ListNode* reverseList(ListNode* head)" },
    fallback: "null",
    tests: [
      { input: "1 → 2 → 3 → 4 → 5", expected: "5 → 4 → 3 → 2 → 1" },
      { input: "1 → 2", expected: "2 → 1" },
      { input: "(empty list)", expected: "(empty list)" }
    ]
  },
  Stacks: {
    title: "Valid Parentheses",
    description: "Given a string containing only ()[]{} characters, return true if every bracket is closed by the same type in the correct order.",
    examples: [
      { input: "s = \"()[]{}\"", output: "true" },
      { input: "s = \"(]\"", output: "false" }
    ],
    constraints: ["1 ≤ s.length ≤ 10⁴", "Use a stack for O(n) time."],
    signature: { Java: "public boolean isValid(String s)", Python: "def is_valid(s):", JavaScript: "function isValid(s)", "C++": "bool isValid(string s)" },
    fallback: "false",
    tests: [
      { input: "\"()[]{}\"", expected: "true" },
      { input: "\"{[]}\"", expected: "true" },
      { input: "\"([)]\"", expected: "false" }
    ]
  },
  Queues: {
    title: "Generate Binary Numbers",
    description: "Given n, return the binary representations of the numbers 1 to n in order, using a queue.",
    examples: [
      { input: "n = 3", output: "[\"1\", \"10\", \"11\"]" },
      { input: "n = 5", output: "[\"1\", \"10\", \"11\", \"100\", \"101\"]" }
    ],
    constraints: ["1 ≤ n ≤ 10⁴", "Use a queue: dequeue x, then enqueue x + \"0\" and x + \"1\"."],
    signature: { Java: "public List<String> generateBinary(int n)", Python: "def generate_binary(n):", JavaScript: "function generateBinary(n)", "C++": "vector<string> generateBinary(int n)" },
    fallback: "empty list",
    tests: [
      { input: "1", expected: "[\"1\"]" },
      { input: "3", expected: "[\"1\", \"10\", \"11\"]" },
      { input: "5", expected: "[\"1\", \"10\", \"11\", \"100\", \"101\"]" }
    ]
  },
  Trees: {
    title: "Maximum Depth of Binary Tree",
    description: "Return the maximum depth of a binary tree — the number of nodes on the longest path from the root to a leaf. A TreeNode class is provided.",
    examples: [
      { input: "root = [3, 9, 20, null, null, 15, 7]", output: "3" },
      { input: "root = [1, null, 2]", output: "2" }
    ],
    constraints: ["0 ≤ number of nodes ≤ 10⁴", "Recursive or BFS solutions are both acceptable."],
    signature: { Java: "public int maxDepth(TreeNode root)", Python: "def max_depth(root):", JavaScript: "function maxDepth(root)", "C++": "int maxDepth(TreeNode* root)" },
    fallback: "0",
    tests: [
      { input: "[3, 9, 20, null, null, 15, 7]", expected: "3" },
      { input: "[1, null, 2]", expected: "2" },
      { input: "[]", expected: "0" }
    ]
  },
  Searching: {
    title: "Binary Search",
    description: "Given a sorted array of distinct integers and a target, return the index of the target or -1 if it is not present.",
    examples: [
      { input: "nums = [-1, 0, 3, 5, 9, 12], target = 9", output: "4" },
      { input: "nums = [-1, 0, 3, 5, 9, 12], target = 2", output: "-1" }
    ],
    constraints: ["1 ≤ nums.length ≤ 10⁴", "nums is sorted in ascending order.", "Required time complexity: O(log n)."],
    signature: { Java: "public int search(int[] nums, int target)", Python: "def search(nums, target):", JavaScript: "function search(nums, target)", "C++": "int search(vector<int>& nums, int target)" },
    fallback: "-1",
    tests: [
      { input: "[-1, 0, 3, 5, 9, 12], 9", expected: "4" },
      { input: "[-1, 0, 3, 5, 9, 12], 2", expected: "-1" },
      { input: "[5], 5", expected: "0" }
    ]
  },
  Sorting: {
    title: "Sort Colors",
    description: "Given an array containing only 0s, 1s and 2s, sort it in place in a single pass without using a library sort.",
    examples: [
      { input: "nums = [2, 0, 2, 1, 1, 0]", output: "[0, 0, 1, 1, 2, 2]" },
      { input: "nums = [2, 0, 1]", output: "[0, 1, 2]" }
    ],
    constraints: ["1 ≤ nums.length ≤ 300", "Hint: Dutch National Flag algorithm."],
    signature: { Java: "public void sortColors(int[] nums)", Python: "def sort_colors(nums):", JavaScript: "function sortColors(nums)", "C++": "void sortColors(vector<int>& nums)" },
    fallback: "",
    tests: [
      { input: "[2, 0, 2, 1, 1, 0]", expected: "[0, 0, 1, 1, 2, 2]" },
      { input: "[2, 0, 1]", expected: "[0, 1, 2]" },
      { input: "[0]", expected: "[0]" }
    ]
  },
  Hashing: {
    title: "First Non-Repeating Character",
    description: "Return the index of the first character in the string that does not repeat. Return -1 if every character repeats.",
    examples: [
      { input: "s = \"leetcode\"", output: "0", explanation: "'l' at index 0 appears only once." },
      { input: "s = \"aabb\"", output: "-1" }
    ],
    constraints: ["1 ≤ s.length ≤ 10⁵", "s contains lowercase English letters."],
    signature: { Java: "public int firstUniqChar(String s)", Python: "def first_uniq_char(s):", JavaScript: "function firstUniqChar(s)", "C++": "int firstUniqChar(string s)" },
    fallback: "-1",
    tests: [
      { input: "\"leetcode\"", expected: "0" },
      { input: "\"loveleetcode\"", expected: "2" },
      { input: "\"aabb\"", expected: "-1" }
    ]
  },
  "Dynamic Programming": {
    title: "Climbing Stairs",
    description: "You can climb 1 or 2 steps at a time. Return the number of distinct ways to reach the top of a staircase with n steps.",
    examples: [
      { input: "n = 2", output: "2", explanation: "1+1 or 2" },
      { input: "n = 3", output: "3", explanation: "1+1+1, 1+2 or 2+1" }
    ],
    constraints: ["1 ≤ n ≤ 45", "Aim for O(n) time and O(1) space."],
    signature: { Java: "public int climbStairs(int n)", Python: "def climb_stairs(n):", JavaScript: "function climbStairs(n)", "C++": "int climbStairs(int n)" },
    fallback: "0",
    tests: [
      { input: "2", expected: "2" },
      { input: "3", expected: "3" },
      { input: "5", expected: "8" }
    ]
  }
};

export function buildStarterCode(problem, language) {
  const signature = problem.signature[language];
  const isVoid = problem.fallback === "";
  const fallbackByLanguage = {
    Java: problem.fallback === "empty list" ? "new ArrayList<>()" : problem.fallback,
    Python: problem.fallback === "null" ? "None" : problem.fallback === "empty list" ? "[]" : problem.fallback === "true" ? "True" : problem.fallback === "false" ? "False" : problem.fallback,
    JavaScript: problem.fallback === "empty list" ? "[]" : problem.fallback,
    "C++": problem.fallback === "null" ? "nullptr" : problem.fallback === "empty list" ? "{}" : problem.fallback
  };
  const returnLine = isVoid ? "" : `return ${fallbackByLanguage[language]};`;

  switch (language) {
    case "Java":
      return `class Solution {\n\n    ${signature} {\n        // Write your solution here\n        ${returnLine}\n    }\n}\n`;
    case "Python":
      return `${signature}\n    # Write your solution here\n    ${isVoid ? "pass" : returnLine.replace(";", "")}\n`;
    case "JavaScript":
      return `${signature} {\n  // Write your solution here\n  ${returnLine}\n}\n`;
    case "C++":
      return `class Solution {\npublic:\n    ${signature} {\n        // Write your solution here\n        ${returnLine}\n    }\n};\n`;
    default:
      return "";
  }
}

/* =====================================================
   SQL
===================================================== */

export const sqlTopics = [
  "SELECT",
  "WHERE",
  "JOIN",
  "GROUP BY",
  "HAVING",
  "Subqueries",
  "CTEs",
  "Window Functions"
];

export const sqlDatasets = {
  "HR Company": {
    description: "A small company with employees, departments and salary records.",
    tables: [
      {
        name: "employees",
        columns: ["id INT PK", "name VARCHAR", "department_id INT FK", "hire_date DATE"],
        sample: [["1", "Asha Rao", "10", "2021-03-15"], ["2", "Vikram Shah", "20", "2022-07-01"], ["3", "Neha Iyer", "10", "2023-01-09"]]
      },
      {
        name: "departments",
        columns: ["id INT PK", "name VARCHAR", "location VARCHAR"],
        sample: [["10", "Engineering", "Pune"], ["20", "Sales", "Mumbai"], ["30", "HR", "Bengaluru"]]
      },
      {
        name: "salaries",
        columns: ["employee_id INT FK", "amount INT", "effective_date DATE"],
        sample: [["1", "98000", "2024-04-01"], ["2", "92000", "2024-04-01"], ["3", "85000", "2024-04-01"]]
      }
    ]
  },
  "Retail Store": {
    description: "An online store with customers, products and orders.",
    tables: [
      {
        name: "customers",
        columns: ["id INT PK", "name VARCHAR", "city VARCHAR"],
        sample: [["1", "Rahul Mehta", "Pune"], ["2", "Sara Khan", "Delhi"], ["3", "Ishaan Das", "Pune"]]
      },
      {
        name: "products",
        columns: ["id INT PK", "name VARCHAR", "category VARCHAR", "price INT"],
        sample: [["1", "Laptop", "Electronics", "55000"], ["2", "Headphones", "Electronics", "3500"], ["3", "Desk Chair", "Furniture", "7500"]]
      },
      {
        name: "orders",
        columns: ["id INT PK", "customer_id INT FK", "product_id INT FK", "quantity INT", "order_date DATE", "total INT"],
        sample: [["101", "1", "1", "1", "2024-05-02", "55000"], ["102", "2", "2", "2", "2024-05-03", "7000"], ["103", "1", "3", "1", "2024-05-05", "7500"]]
      }
    ]
  }
};

// mustInclude: keywords the submitted query is checked for in demo mode.
export const sqlProblems = {
  "HR Company": {
    SELECT: {
      question: "List the name and hire date of every employee.",
      hint: "Select two columns from the employees table.",
      mustInclude: ["SELECT", "FROM EMPLOYEES"],
      expected: { columns: ["name", "hire_date"], rows: [["Asha Rao", "2021-03-15"], ["Vikram Shah", "2022-07-01"], ["Neha Iyer", "2023-01-09"]] }
    },
    WHERE: {
      question: "Find employees hired on or after 2022-01-01.",
      hint: "Filter hire_date with a WHERE clause.",
      mustInclude: ["WHERE", "HIRE_DATE"],
      expected: { columns: ["name", "hire_date"], rows: [["Vikram Shah", "2022-07-01"], ["Neha Iyer", "2023-01-09"]] }
    },
    JOIN: {
      question: "Show each employee's name together with their department name.",
      hint: "Join employees to departments on department_id.",
      mustInclude: ["JOIN", "DEPARTMENTS"],
      expected: { columns: ["employee", "department"], rows: [["Asha Rao", "Engineering"], ["Vikram Shah", "Sales"], ["Neha Iyer", "Engineering"]] }
    },
    "GROUP BY": {
      question: "Count the number of employees in each department.",
      hint: "GROUP BY department and use COUNT(*).",
      mustInclude: ["GROUP BY", "COUNT"],
      expected: { columns: ["department", "employee_count"], rows: [["Engineering", "2"], ["Sales", "1"]] }
    },
    HAVING: {
      question: "List departments that have more than one employee.",
      hint: "Filter groups with HAVING COUNT(*) > 1.",
      mustInclude: ["GROUP BY", "HAVING"],
      expected: { columns: ["department", "employee_count"], rows: [["Engineering", "2"]] }
    },
    Subqueries: {
      question: "Find the second highest salary.",
      hint: "Find the maximum salary that is lower than the overall maximum.",
      mustInclude: ["(SELECT", "MAX"],
      expected: { columns: ["salary"], rows: [["92000"]] }
    },
    CTEs: {
      question: "Using a CTE, find the average salary in each department.",
      hint: "Start with WITH dept_salary AS (...) and then aggregate.",
      mustInclude: ["WITH", "AVG"],
      expected: { columns: ["department", "avg_salary"], rows: [["Engineering", "91500"], ["Sales", "92000"]] }
    },
    "Window Functions": {
      question: "Rank employees by salary within each department.",
      hint: "Use RANK() OVER (PARTITION BY department_id ORDER BY amount DESC).",
      mustInclude: ["OVER", "PARTITION BY"],
      expected: { columns: ["name", "department", "salary", "salary_rank"], rows: [["Asha Rao", "Engineering", "98000", "1"], ["Neha Iyer", "Engineering", "85000", "2"], ["Vikram Shah", "Sales", "92000", "1"]] }
    }
  },
  "Retail Store": {
    SELECT: {
      question: "List the name and price of every product.",
      hint: "Select two columns from the products table.",
      mustInclude: ["SELECT", "FROM PRODUCTS"],
      expected: { columns: ["name", "price"], rows: [["Laptop", "55000"], ["Headphones", "3500"], ["Desk Chair", "7500"]] }
    },
    WHERE: {
      question: "Find Electronics products priced above ₹5,000.",
      hint: "Combine two conditions with AND.",
      mustInclude: ["WHERE", "CATEGORY"],
      expected: { columns: ["name", "price"], rows: [["Laptop", "55000"]] }
    },
    JOIN: {
      question: "Show every order ID with the customer's name.",
      hint: "Join orders to customers on customer_id.",
      mustInclude: ["JOIN", "CUSTOMERS"],
      expected: { columns: ["order_id", "customer"], rows: [["101", "Rahul Mehta"], ["102", "Sara Khan"], ["103", "Rahul Mehta"]] }
    },
    "GROUP BY": {
      question: "Calculate the total order value for each city.",
      hint: "Join orders to customers, then GROUP BY city with SUM(total).",
      mustInclude: ["GROUP BY", "SUM"],
      expected: { columns: ["city", "total_value"], rows: [["Pune", "62500"], ["Delhi", "7000"]] }
    },
    HAVING: {
      question: "List customers who placed more than one order.",
      hint: "GROUP BY customer and filter with HAVING COUNT(*) > 1.",
      mustInclude: ["GROUP BY", "HAVING"],
      expected: { columns: ["customer", "order_count"], rows: [["Rahul Mehta", "2"]] }
    },
    Subqueries: {
      question: "Find products priced above the average product price.",
      hint: "Compare price with (SELECT AVG(price) FROM products).",
      mustInclude: ["(SELECT", "AVG"],
      expected: { columns: ["name", "price"], rows: [["Laptop", "55000"]] }
    },
    CTEs: {
      question: "Using a CTE, find the customer with the highest total spend.",
      hint: "Aggregate spend per customer in a CTE, then order by it.",
      mustInclude: ["WITH", "SUM"],
      expected: { columns: ["customer", "total_spend"], rows: [["Rahul Mehta", "62500"]] }
    },
    "Window Functions": {
      question: "Show a running total of order value by order date.",
      hint: "Use SUM(total) OVER (ORDER BY order_date).",
      mustInclude: ["OVER", "ORDER BY"],
      expected: { columns: ["order_date", "total", "running_total"], rows: [["2024-05-02", "55000", "55000"], ["2024-05-03", "7000", "62000"], ["2024-05-05", "7500", "69500"]] }
    }
  }
};

/* =====================================================
   SYSTEM DESIGN
===================================================== */

export const systemDesignSections = [
  { id: "requirements", label: "Requirements", guide: "Functional and non-functional requirements, scale estimates." },
  { id: "api", label: "API Design", guide: "Key endpoints, request and response shapes." },
  { id: "database", label: "Database", guide: "Data model, SQL vs NoSQL, indexes." },
  { id: "architecture", label: "Architecture", guide: "Main components and how requests flow." },
  { id: "caching", label: "Caching", guide: "What to cache, where, and invalidation." },
  { id: "scaling", label: "Scaling", guide: "Load balancing, sharding, replication, queues." },
  { id: "security", label: "Security", guide: "Authentication, rate limiting, abuse prevention." },
  { id: "tradeoffs", label: "Trade-offs", guide: "Alternatives you considered and why you chose this design." }
];

export const systemDesignChallenges = {
  "URL Shortener": {
    summary: "Design a service like bit.ly that turns long URLs into short links and redirects visitors.",
    scale: "100M new URLs per month · 10:1 read-to-write ratio",
    hints: {
      requirements: "Create short link, redirect, optional custom alias and expiry, analytics.",
      api: "POST /api/links, GET /{code} → 301/302 redirect.",
      database: "Key-value mapping code → long URL; consider unique code generation.",
      architecture: "API servers, ID generator, database, redirect service.",
      caching: "Cache hot short codes in Redis close to the redirect path.",
      scaling: "Read replicas, sharding by code, CDN for redirects.",
      security: "Rate-limit creation, scan malicious URLs, prevent enumeration.",
      tradeoffs: "Hash vs counter-based IDs; 301 (cacheable) vs 302 (trackable)."
    }
  },
  "Chat Application": {
    summary: "Design a one-to-one and group chat service with online presence and message history.",
    scale: "50M daily active users · messages delivered in under 200 ms",
    hints: {
      requirements: "1:1 and group chat, delivery receipts, presence, history.",
      api: "WebSocket connection for messages; REST for history and groups.",
      database: "Wide-column store for messages partitioned by conversation.",
      architecture: "Connection gateways, chat service, message queue, presence service.",
      caching: "Recent conversations and presence status in memory.",
      scaling: "Shard by conversation ID; scale gateways horizontally.",
      security: "End-to-end or transport encryption, authentication on connect.",
      tradeoffs: "Push vs pull delivery; consistency vs latency for ordering."
    }
  },
  "E-commerce Platform": {
    summary: "Design the core of an online store: catalogue, cart, checkout and orders.",
    scale: "5M daily users · flash sales with 20× traffic spikes",
    hints: {
      requirements: "Browse catalogue, cart, checkout, payments, order tracking.",
      api: "/products, /cart, /orders, payment callbacks.",
      database: "Relational for orders and inventory; search index for catalogue.",
      architecture: "Microservices for catalogue, cart, order, payment, inventory.",
      caching: "Product pages and prices in a CDN / Redis.",
      scaling: "Queue checkout requests during spikes; autoscale stateless services.",
      security: "PCI compliance via payment gateway, idempotent payment requests.",
      tradeoffs: "Strong consistency for inventory vs availability during sales."
    }
  },
  "Video Streaming": {
    summary: "Design a platform that lets users upload videos and stream them smoothly on any device.",
    scale: "1M uploads per day · 100M daily viewers",
    hints: {
      requirements: "Upload, transcode, stream at adaptive quality, recommendations.",
      api: "Resumable upload API, playback manifest endpoint.",
      database: "Metadata in a relational DB; video files in object storage.",
      architecture: "Upload service, transcoding workers, object storage, CDN.",
      caching: "CDN edge caching of video segments.",
      scaling: "Parallel transcoding queues, multi-region CDN.",
      security: "Signed URLs, DRM for premium content, upload scanning.",
      tradeoffs: "Pre-transcoding all resolutions vs on-demand transcoding cost."
    }
  },
  "Rate Limiter": {
    summary: "Design a rate limiter that protects CareerAI's public APIs from abuse.",
    scale: "50K requests per second across many API servers",
    hints: {
      requirements: "Limit per user/IP/API key, configurable rules, low latency.",
      api: "Middleware check; return 429 with Retry-After.",
      database: "Counters in Redis with expiry.",
      architecture: "Gateway-level limiter backed by a shared counter store.",
      caching: "Local token buckets with periodic sync to reduce Redis calls.",
      scaling: "Shard counters by key; Redis cluster.",
      security: "Prevent bypass via header spoofing; protect the limiter itself.",
      tradeoffs: "Fixed window vs sliding window vs token bucket accuracy."
    }
  },
  "Notification System": {
    summary: "Design a service that sends email, SMS and push notifications reliably.",
    scale: "20M notifications per day · retries for failed providers",
    hints: {
      requirements: "Multiple channels, templates, scheduling, user preferences.",
      api: "POST /notifications with channel, template and recipient.",
      database: "Notification log with status; preferences per user.",
      architecture: "API, queue per channel, workers, third-party providers.",
      caching: "Templates and user preferences.",
      scaling: "Partitioned queues, horizontal workers, provider failover.",
      security: "Validate senders, avoid leaking personal data in logs.",
      tradeoffs: "At-least-once delivery vs duplicates; idempotency keys."
    }
  }
};

/* =====================================================
   VOICE
===================================================== */

export const voiceTopics = {
  "Tell Me About Yourself": {
    questions: ["Tell me about yourself.", "Walk me through your background and why you are applying for this role."],
    keywords: ["experience", "project", "skill", "role", "currently", "studied", "graduat", "interest", "goal"],
    tips: ["Use present → past → future.", "Keep it under 90 seconds.", "End with why this role fits you."]
  },
  "Project Explanation": {
    questions: ["Explain your final-year project.", "Describe a project you are proud of and your role in it."],
    keywords: ["problem", "built", "used", "react", "spring", "database", "team", "role", "result", "challenge"],
    tips: ["Start with the problem it solves.", "Explain your specific contribution.", "Mention one technical challenge."]
  },
  "Technical Answers": {
    questions: ["Explain REST APIs to a non-technical interviewer.", "How would you explain the difference between SQL and NoSQL databases?"],
    keywords: ["example", "because", "for instance", "data", "request", "response", "use", "difference"],
    tips: ["Define the concept in one sentence.", "Give a simple example.", "Compare with an alternative."]
  },
  "HR Questions": {
    questions: ["Why should we hire you?", "Where do you see yourself in five years?"],
    keywords: ["skill", "learn", "team", "contribute", "grow", "company", "value", "goal"],
    tips: ["Connect your strengths to the job.", "Be specific and genuine.", "Show motivation to learn."]
  },
  "Behavioural Questions": {
    questions: ["Describe a challenge you faced and how you handled it.", "Tell me about a time you worked in a team with a disagreement."],
    keywords: ["situation", "task", "action", "result", "learned", "team", "resolved", "challenge"],
    tips: ["Use a real example.", "Focus on your actions.", "Share what you learned."]
  },
  "STAR Method": {
    questions: ["Using the STAR method, describe a time you met a tight deadline.", "Using STAR, describe a time you fixed a difficult bug."],
    keywords: ["situation", "task", "action", "result", "deadline", "bug", "learned"],
    tips: ["Situation: set the context briefly.", "Task: your responsibility.", "Action: what you did.", "Result: the outcome."]
  }
};

/* =====================================================
   DAILY CHALLENGE POOLS (one item picked per category per day)
===================================================== */

export const dailyPools = {
  Technical: [
    { question: "Which HTTP status code means 'Not Found'?", options: ["404", "400", "401", "500"], answer: "404", explanation: "404 indicates the server cannot find the requested resource.", tag: "REST APIs" },
    { question: "Which Java collection keeps elements sorted?", options: ["TreeSet", "HashSet", "ArrayList", "LinkedList"], answer: "TreeSet", explanation: "TreeSet stores elements in sorted order using a red-black tree.", tag: "Java" },
    { question: "Which React hook runs side effects?", options: ["useEffect", "useState", "useMemo", "useId"], answer: "useEffect", explanation: "useEffect runs after render for side effects such as data fetching or subscriptions.", tag: "React" },
    { question: "What does ACID's 'D' stand for?", options: ["Durability", "Dependency", "Distribution", "Deletion"], answer: "Durability", explanation: "Durability means committed transactions survive crashes.", tag: "DBMS" }
  ],
  Aptitude: [
    { question: "What is 25% of 640?", options: ["160", "150", "180", "120"], answer: "160", explanation: "640 × 25/100 = 160.", tag: "Percentages" },
    { question: "A can finish a job in 12 days and B in 6 days. Together they take?", options: ["4 days", "6 days", "3 days", "9 days"], answer: "4 days", explanation: "1/12 + 1/6 = 3/12 = 1/4, so 4 days.", tag: "Time & Work" },
    { question: "Find the next number: 3, 9, 27, 81, ?", options: ["243", "162", "324", "108"], answer: "243", explanation: "Each term is multiplied by 3.", tag: "Logical Reasoning" },
    { question: "An item costing ₹800 is sold at 25% profit. Selling price?", options: ["₹1,000", "₹900", "₹1,025", "₹950"], answer: "₹1,000", explanation: "800 × 1.25 = 1,000.", tag: "Profit & Loss" }
  ],
  SQL: [
    { question: "Which clause filters groups after aggregation?", options: ["HAVING", "WHERE", "ORDER BY", "LIMIT"], answer: "HAVING", explanation: "WHERE filters rows before grouping; HAVING filters groups after aggregation.", tag: "SQL" },
    { question: "Which JOIN returns all rows from the left table?", options: ["LEFT JOIN", "INNER JOIN", "RIGHT JOIN", "CROSS JOIN"], answer: "LEFT JOIN", explanation: "LEFT JOIN keeps every left row, filling NULLs when there is no match.", tag: "SQL" },
    { question: "Which function assigns the same rank to ties and skips the next rank?", options: ["RANK()", "ROW_NUMBER()", "DENSE_RANK()", "NTILE()"], answer: "RANK()", explanation: "RANK() leaves gaps after ties; DENSE_RANK() does not.", tag: "SQL" },
    { question: "Which keyword removes duplicate rows from a result?", options: ["DISTINCT", "UNIQUE", "GROUP", "FILTER"], answer: "DISTINCT", explanation: "SELECT DISTINCT returns unique rows only.", tag: "SQL" }
  ],
  Coding: [
    { question: "What is the time complexity of binary search?", options: ["O(log n)", "O(n)", "O(n log n)", "O(1)"], answer: "O(log n)", explanation: "Binary search halves the search space each step.", tag: "Coding" },
    { question: "Which data structure is best for checking balanced parentheses?", options: ["Stack", "Queue", "Heap", "Graph"], answer: "Stack", explanation: "A stack matches the most recent opening bracket first (LIFO).", tag: "Coding" },
    { question: "What does this return for n = 5? f(n) = n <= 1 ? n : f(n-1) + f(n-2)", options: ["5", "8", "3", "13"], answer: "5", explanation: "This is Fibonacci: f(5) = 5.", tag: "Coding" },
    { question: "Average time complexity of a HashMap lookup?", options: ["O(1)", "O(log n)", "O(n)", "O(n²)"], answer: "O(1)", explanation: "Hashing maps a key directly to a bucket on average.", tag: "Coding" }
  ]
};
