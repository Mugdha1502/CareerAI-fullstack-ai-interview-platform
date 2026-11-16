import {
  FiCode,
  FiMonitor,
  FiServer,
  FiDatabase,
  FiBarChart2,
  FiCpu,
  FiCloud,
  FiShield,
  FiSmartphone,
  FiSettings,
  FiActivity,
  FiBriefcase,
  FiTarget,
  FiCheckSquare,
  FiLayers,
  FiZap,
  FiEye,
  FiLock
} from "react-icons/fi";
import { technicalTopics } from "../practice/technicalMockData";

/* =====================================================
   CAREER TRACK DATA (frontend demo)

   TODO backend: this structure mirrors the future
   React → Spring Boot → CareerTrackService → MySQL response.
   Keep field names stable so the API can replace this file.
===================================================== */

export const SELECTED_TRACK_KEY = "careerai_selected_track";

/** "Spring Security" → "spring-security", "C# & .NET" → "csharp-net" */
export function toTopicId(label) {
  return label
    .toLowerCase()
    .replace(/c#/g, "csharp")
    .replace(/\+/g, "plus")
    .replace(/[.']/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

// Roadmap step helper: title, short purpose, key concepts, optional overrides.
const step = (title, purpose, concepts, extra = {}) => ({
  id: toTopicId(title),
  title,
  purpose,
  concepts,
  ...extra
});

const project = (title, tech, difficulty, demonstrates) => ({
  title,
  tech,
  difficulty,
  demonstrates
});


/* =====================================================
   COMMON MISTAKES BY DOMAIN
   Used when a topic has no topic-specific list.
===================================================== */

const domainMistakes = {
  development: [
    "Memorising definitions without being able to write or explain working code.",
    "Skipping error handling and edge cases in practice projects.",
    "Not being able to connect the topic to a feature you actually built."
  ],
  data: [
    "Jumping to tools before understanding the business question.",
    "Ignoring data quality — missing values, duplicates and outliers.",
    "Presenting numbers without explaining what decision they support."
  ],
  cloud: [
    "Clicking through consoles without understanding what was created.",
    "Ignoring cost, security and failure scenarios.",
    "Not automating repeatable steps or documenting the setup."
  ],
  security: [
    "Memorising tool names without understanding the underlying attack or control.",
    "Testing or scanning systems without written authorisation.",
    "Reporting findings without impact, evidence and a clear remediation."
  ],
  quality: [
    "Writing test cases that only cover the happy path.",
    "Logging bugs without clear steps, expected and actual results.",
    "Automating unstable flows before the manual test is well understood."
  ],
  business: [
    "Documenting solutions before understanding the actual problem.",
    "Writing vague requirements that cannot be tested.",
    "Not confirming understanding with stakeholders before sign-off."
  ]
};


/* =====================================================
   TOPIC LIBRARY
   Richer content for topics that appear across tracks.
   Keyed by topic ID.
===================================================== */

const topicLibrary = {
  "java-fundamentals": {
    know: "Java syntax, primitive and reference types, control flow, methods, strings and arrays — plus how code is compiled to bytecode and run on the JVM.",
    mistakes: [
      "Comparing strings with == instead of equals().",
      "Confusing JDK, JRE and JVM.",
      "Not knowing why String is immutable."
    ]
  },
  "object-oriented-programming": {
    know: "The four OOP principles — encapsulation, inheritance, polymorphism and abstraction — and how interfaces and abstract classes model real designs.",
    mistakes: [
      "Explaining OOP with only animal/vehicle examples instead of your own project.",
      "Overusing inheritance where composition is clearer.",
      "Mixing up overloading and overriding."
    ]
  },
  "spring-security": {
    know: "How Spring Security's filter chain authenticates requests, how passwords are hashed with BCrypt, how JWT-based stateless authentication works and how roles control access to endpoints.",
    mistakes: [
      "Storing plain-text passwords or weak hashes.",
      "Confusing 401 Unauthorized with 403 Forbidden.",
      "Putting JWT signing secrets in frontend code or issuing tokens that never expire."
    ]
  },
  "docker-fundamentals": {
    know: "How images are built from Dockerfiles, how containers run them, and how Docker Compose runs an API and database together with volumes and environment variables.",
    mistakes: [
      "Describing containers as lightweight virtual machines.",
      "Using localhost to reach another container.",
      "Baking secrets or credentials into the image."
    ]
  },
  "docker-containers": {
    know: "Images, containers, Dockerfiles, volumes and networks — and why containers make builds reproducible across environments.",
    mistakes: [
      "Running everything as root inside containers.",
      "Creating huge images instead of using slim bases and multi-stage builds.",
      "Losing data by not mounting volumes."
    ]
  },
  "rest-apis": {
    know: "Resource-oriented URLs, HTTP methods, status codes, request/response JSON, validation, pagination and versioning.",
    mistakes: [
      "Using verbs in URLs such as /getUsers.",
      "Returning 200 for errors.",
      "Ignoring validation and consistent error responses."
    ]
  },
  "sql-mysql": {
    know: "Writing SELECT queries with filtering, joins, grouping and subqueries, and designing normalised tables with keys and indexes.",
    mistakes: [
      "Forgetting that WHERE filters rows and HAVING filters groups.",
      "Using SELECT * in application queries.",
      "Not knowing when an index helps or hurts."
    ]
  },
  "sql-fundamentals": {
    know: "SELECT, WHERE, ORDER BY, aggregate functions, GROUP BY and JOINs over realistic business tables.",
    mistakes: [
      "Mixing up INNER and LEFT JOIN results.",
      "Filtering aggregates with WHERE instead of HAVING.",
      "Not checking for duplicate rows after a join."
    ]
  },
  "git-github": {
    know: "Commits, branches, merges, pull requests and resolving conflicts in a team workflow.",
    mistakes: [
      "Committing directly to main.",
      "Writing commit messages like 'fix' or 'changes'.",
      "Rebasing branches other people already pulled."
    ]
  },
  "data-structures": {
    know: "Arrays, strings, hash maps, stacks, queues, trees and graphs — and analysing time and space complexity with Big-O.",
    mistakes: [
      "Coding immediately without clarifying the problem and edge cases.",
      "Not stating time and space complexity.",
      "Memorising solutions instead of recognising patterns."
    ]
  },
  "linux-fundamentals": {
    know: "The file system, permissions, processes, package management, logs and shell commands used on servers every day.",
    mistakes: [
      "Using chmod 777 to 'fix' permission problems.",
      "Not knowing where to find service logs.",
      "Running commands as root by default."
    ]
  },
  "ci-cd-pipelines": {
    know: "How code moves from commit to production: build, test, artifact, deploy and rollback stages, usually in GitHub Actions, Jenkins or GitLab CI.",
    mistakes: [
      "Pipelines without automated tests.",
      "Storing secrets in pipeline files.",
      "No rollback plan for failed deployments."
    ]
  },
  "statistics": {
    know: "Descriptive statistics, distributions, sampling, hypothesis testing, confidence intervals and correlation versus causation.",
    mistakes: [
      "Treating correlation as causation.",
      "Reporting averages without spread or outliers.",
      "Misinterpreting p-values."
    ]
  },
  "machine-learning-fundamentals": {
    know: "Supervised and unsupervised learning, train/validation/test splits, overfitting, bias-variance and common algorithms such as regression, trees and clustering.",
    mistakes: [
      "Evaluating on the training data.",
      "Using accuracy on imbalanced data.",
      "Leaking target information into features."
    ]
  },
  "networking-fundamentals": {
    know: "The OSI and TCP/IP models, IP addressing, subnets, DNS, HTTP/HTTPS, common ports and how traffic flows through firewalls.",
    mistakes: [
      "Not knowing common ports and protocols.",
      "Confusing TCP and UDP use cases.",
      "Unable to explain what happens when you open a URL."
    ]
  },
  "react": {
    know: "Components, props, state, hooks, effects, lists and forms — and how React re-renders when state changes.",
    mistakes: [
      "Mutating state directly.",
      "Missing keys or using indexes as keys for dynamic lists.",
      "Putting everything into useEffect."
    ]
  },
  "testing-fundamentals": {
    know: "Test levels (unit, integration, system, acceptance), test types and how to design test cases with boundary values and equivalence partitioning.",
    mistakes: [
      "Testing only valid inputs.",
      "Unclear expected results in test cases.",
      "Not tracing tests back to requirements."
    ]
  }
};


/* =====================================================
   TRACKS
===================================================== */

export const careerTracks = {

  /* ---------- SOFTWARE DEVELOPMENT ---------- */

  "java-full-stack": {
    title: "Java Full Stack Developer",
    icon: FiCode,
    category: "Software Development",
    domain: "development",
    cardDescription: "Java, Spring Boot, REST APIs, SQL, React, security and deployment.",
    description: "Prepare Java, Spring Boot, SQL, REST APIs, React, security and deployment fundamentals.",
    overview: "Java full stack developers build complete web applications: secure Spring Boot APIs backed by relational databases, and React interfaces that consume them. Interviews test core Java depth, backend design and your ability to explain a real end-to-end project.",
    level: "Beginner to Intermediate",
    focus: "Backend + Frontend",
    skills: ["Java", "OOP", "Collections", "SQL", "Spring Boot", "REST APIs", "JPA", "Hibernate", "Spring Security", "HTML", "CSS", "JavaScript", "React", "Git", "Docker"],
    roadmap: [
      step("Java Fundamentals", "Write clean Java with types, control flow, strings and arrays.", ["JVM, JRE and JDK", "Primitive vs reference types", "String immutability", "Methods and arrays"], { practice: "Java" }),
      step("Object-Oriented Programming", "Model real problems with classes, interfaces and inheritance.", ["Encapsulation", "Inheritance vs composition", "Polymorphism", "Interfaces vs abstract classes"], { practice: "Java" }),
      step("Collections Framework", "Choose the right data structure for application data.", ["List, Set and Map", "HashMap internals", "Comparable vs Comparator", "Streams API"], { practice: "Java" }),
      step("Exception Handling", "Handle failures predictably in services and APIs.", ["Checked vs unchecked exceptions", "try-with-resources", "Custom exceptions", "Global exception handlers"], { practice: "Java" }),
      step("SQL & MySQL", "Design tables and query application data efficiently.", ["Joins", "GROUP BY and HAVING", "Indexes", "Normalisation"]),
      step("Spring Boot", "Build production-style backend services quickly.", ["Dependency injection", "Auto-configuration", "Profiles and properties", "Layered architecture"]),
      step("REST APIs", "Expose clean, predictable endpoints for the frontend.", ["HTTP methods and status codes", "DTOs and validation", "Pagination", "Error responses"]),
      step("JPA & Hibernate", "Map Java entities to relational tables.", ["Entities and relationships", "Lazy vs eager loading", "N+1 query problem", "Transactions"], { practice: "Spring Boot" }),
      step("Spring Security", "Protect APIs with authentication and role-based access.", ["SecurityFilterChain", "BCrypt password hashing", "JWT authentication", "Role-based authorization"]),
      step("React", "Build the user interface that consumes your APIs.", ["Components and props", "useState and useEffect", "Forms and validation", "Calling REST APIs"]),
      step("Git & GitHub", "Collaborate with branches and pull requests.", ["Branching", "Pull requests", "Merge conflicts", "Commit hygiene"]),
      step("Docker Fundamentals", "Package and run the API and database consistently.", ["Dockerfile", "Images vs containers", "Docker Compose", "Volumes and environment variables"])
    ],
    practiceTopics: ["Java", "Spring Boot", "Spring Security", "REST APIs", "React"],
    interviewFocus: ["Java", "OOP", "Collections", "Spring Boot", "REST APIs", "SQL", "Spring Security", "React", "Project explanation"],
    recommendedProjects: [
      project("E-Commerce REST API", ["Java", "Spring Boot", "MySQL", "JWT"], "Intermediate", "Layered architecture, JPA relationships, secured endpoints and order workflows."),
      project("Job Portal", ["Spring Boot", "React", "MySQL"], "Intermediate", "Full stack CRUD, role-based dashboards for candidates and recruiters, and search."),
      project("Inventory Management System", ["Java", "REST APIs", "SQL"], "Beginner", "Clean REST design, validation, reporting queries and transaction handling.")
    ]
  },

  "frontend-developer": {
    title: "Frontend Developer",
    icon: FiMonitor,
    category: "Software Development",
    domain: "development",
    cardDescription: "HTML, CSS, JavaScript, React, APIs, testing and responsive design.",
    description: "Prepare modern frontend development and UI engineering skills.",
    overview: "Frontend developers turn designs into fast, accessible and responsive interfaces. Interviews focus on JavaScript fundamentals, CSS layout, React patterns, browser behaviour and how you structure real UI code.",
    level: "Beginner to Intermediate",
    focus: "UI Engineering",
    skills: ["HTML", "CSS", "JavaScript", "DOM", "Responsive Design", "React", "Hooks", "REST APIs", "Accessibility", "Git", "Testing"],
    roadmap: [
      step("Semantic HTML", "Structure pages so they are accessible and meaningful.", ["Semantic elements", "Forms and labels", "ARIA basics", "SEO-friendly markup"]),
      step("CSS Layout", "Build layouts that hold up on every screen size.", ["Box model", "Flexbox", "CSS Grid", "Specificity"]),
      step("Responsive Design", "Adapt interfaces from phone to desktop.", ["Mobile-first", "Media queries", "Fluid units", "Responsive images"]),
      step("JavaScript Fundamentals", "Write correct, modern JavaScript.", ["Scope and closures", "this keyword", "Arrays and objects", "ES6+ syntax"]),
      step("Async JavaScript", "Handle network requests and timing safely.", ["Event loop", "Promises", "async/await", "fetch and error handling"], { practice: "JavaScript" }),
      step("DOM & Browser APIs", "Understand what the browser does with your code.", ["Event delegation", "Rendering and reflow", "localStorage", "Debounce and throttle"], { practice: "JavaScript" }),
      step("React", "Build component-based interfaces.", ["Components and props", "State", "Lists and keys", "Controlled forms"]),
      step("React Hooks & State", "Manage state and side effects cleanly.", ["useEffect dependencies", "useMemo and useCallback", "Context", "Custom hooks"], { practice: "React" }),
      step("REST APIs", "Connect the UI to backend data.", ["HTTP methods", "Loading and error states", "Authentication headers", "Pagination"]),
      step("Frontend Testing", "Prevent regressions in UI behaviour.", ["Unit vs integration tests", "React Testing Library", "Mocking APIs", "Accessibility checks"], { practice: null })
    ],
    practiceTopics: ["JavaScript", "React", "REST APIs", "Git"],
    interviewFocus: ["JavaScript", "Closures and the event loop", "CSS layout", "React", "REST APIs", "Accessibility", "Project explanation"],
    recommendedProjects: [
      project("Responsive Portfolio", ["HTML", "CSS", "JavaScript"], "Beginner", "Semantic markup, responsive layout and accessibility basics."),
      project("Job Search Dashboard", ["React", "REST APIs", "CSS"], "Intermediate", "API integration, filters, loading/error states and reusable components."),
      project("Kanban Task Board", ["React", "Hooks", "localStorage"], "Intermediate", "State management, drag-and-drop interactions and persistence.")
    ]
  },

  "backend-developer": {
    title: "Backend Developer",
    icon: FiServer,
    category: "Software Development",
    domain: "development",
    cardDescription: "Backend frameworks, APIs, databases, authentication and deployment.",
    description: "Prepare backend APIs, databases, security and deployment.",
    overview: "Backend developers design the services, data models and integrations behind every application. Interviews test API design, database knowledge, security, performance thinking and how you reason about failures at scale.",
    level: "Intermediate",
    focus: "APIs + Data",
    skills: ["Java or Python", "REST APIs", "SQL", "Database Design", "Authentication", "Caching", "Message Queues", "Docker", "Testing"],
    roadmap: [
      step("HTTP & REST", "Understand how clients and servers communicate.", ["Request/response cycle", "Methods and status codes", "Headers", "Statelessness"], { practice: "REST APIs" }),
      step("API Design", "Design endpoints that are consistent and easy to evolve.", ["Resource naming", "Validation", "Versioning", "Error format"], { practice: "REST APIs" }),
      step("SQL", "Query and aggregate relational data.", ["Joins", "Aggregations", "Subqueries", "Window functions"]),
      step("Database Design", "Model data that stays consistent as the app grows.", ["Normalisation", "Keys and constraints", "Indexes", "Transactions and ACID"]),
      step("Authentication & Authorization", "Know who the user is and what they may do.", ["Sessions vs tokens", "JWT", "Password hashing", "Role-based access"], { practice: "Spring Security" }),
      step("Caching", "Reduce latency and database load.", ["Cache-aside pattern", "Redis", "TTL and invalidation", "HTTP caching"], { practice: null }),
      step("Message Queues", "Process work asynchronously and reliably.", ["Producers and consumers", "Kafka vs RabbitMQ", "Retries", "Idempotency"], { practice: null }),
      step("Backend Testing", "Test business logic and integrations.", ["Unit tests", "Integration tests", "Mocking", "Test databases"], { practice: null }),
      step("Docker", "Ship services consistently across environments.", ["Dockerfile", "Compose", "Environment config", "Health checks"]),
      step("System Design Basics", "Reason about scale, availability and trade-offs.", ["Load balancing", "Replication", "Sharding", "Rate limiting"])
    ],
    practiceTopics: ["REST APIs", "DBMS", "Spring Boot", "Docker"],
    interviewFocus: ["REST APIs", "SQL", "Database design", "Authentication", "Caching", "System design", "Project explanation"],
    recommendedProjects: [
      project("URL Shortener Service", ["REST APIs", "SQL", "Redis"], "Intermediate", "Key generation, caching, redirects and analytics counters."),
      project("Booking API", ["Spring Boot", "MySQL", "JWT"], "Intermediate", "Transactions, concurrency on seats/slots and secured endpoints."),
      project("Notification Service", ["Message Queue", "Docker", "REST APIs"], "Advanced", "Asynchronous processing, retries and idempotent consumers.")
    ]
  },

  "full-stack-developer": {
    title: "Full Stack Developer",
    icon: FiLayers,
    category: "Software Development",
    domain: "development",
    cardDescription: "Frontend, backend, databases, APIs and deployment fundamentals.",
    description: "Prepare to build and ship complete web applications from UI to database.",
    overview: "Full stack developers own features end to end — UI, API, data model and deployment. Interviews check breadth across the stack and depth in at least one area, usually through a project walkthrough.",
    level: "Beginner to Intermediate",
    focus: "End-to-end Features",
    skills: ["HTML", "CSS", "JavaScript", "React", "Node.js or Spring Boot", "REST APIs", "SQL", "Authentication", "Git", "Deployment"],
    roadmap: [
      step("Web Fundamentals", "Understand how browsers, servers and HTTP fit together.", ["HTTP request lifecycle", "DNS and hosting", "HTML/CSS basics", "Browser rendering"], { practice: "Computer Networks" }),
      step("JavaScript", "Use one language confidently across the stack.", ["Closures", "Promises and async/await", "Modules", "Array methods"]),
      step("React", "Build interactive frontends.", ["Components", "State and props", "Routing", "Forms"]),
      step("Backend Framework", "Build APIs with Express or Spring Boot.", ["Routing/controllers", "Middleware", "Validation", "Layered code"], { practice: "Spring Boot" }),
      step("REST APIs", "Connect frontend and backend cleanly.", ["Resource design", "Status codes", "Error handling", "CORS"]),
      step("SQL & Data Modelling", "Store application data reliably.", ["Schema design", "Joins", "Indexes", "Migrations"]),
      step("Authentication", "Add sign-up, login and protected routes.", ["Password hashing", "JWT or sessions", "Protected routes", "Refresh tokens"], { practice: "Spring Security" }),
      step("Git & Collaboration", "Work in a team codebase.", ["Branches", "Pull requests", "Code review", "Conflict resolution"]),
      step("Deployment", "Get your application live.", ["Environment variables", "Docker basics", "Cloud hosting", "CI basics"], { practice: "Docker" })
    ],
    practiceTopics: ["JavaScript", "React", "REST APIs", "Spring Boot"],
    interviewFocus: ["JavaScript", "React", "REST APIs", "SQL", "Authentication", "Deployment", "Project explanation"],
    recommendedProjects: [
      project("Expense Tracker", ["React", "Node.js", "SQL"], "Beginner", "CRUD across the full stack, charts and authentication."),
      project("Learning Platform", ["React", "Spring Boot", "MySQL"], "Intermediate", "Roles, course progress tracking and file uploads."),
      project("Real-time Chat App", ["React", "WebSockets", "Node.js"], "Advanced", "Real-time communication, presence and message persistence.")
    ]
  },

  "python-developer": {
    title: "Python Developer",
    icon: FiCpu,
    category: "Software Development",
    domain: "development",
    cardDescription: "Python, OOP, Django, APIs, SQL and testing.",
    description: "Prepare Python programming, web frameworks, APIs and testing.",
    overview: "Python developers build web backends, automation and data-driven services. Interviews test Pythonic code, OOP, data structures, a web framework such as Django or FastAPI, and testing discipline.",
    level: "Beginner to Intermediate",
    focus: "Backend + Automation",
    skills: ["Python", "OOP", "Data Structures", "Django", "FastAPI", "REST APIs", "SQL", "pytest", "Git"],
    roadmap: [
      step("Python Fundamentals", "Write idiomatic Python.", ["Data types", "List/dict comprehensions", "Functions and *args/**kwargs", "Mutability"], { practice: "Python" }),
      step("Python OOP", "Structure larger programs with classes.", ["Classes and dunder methods", "Inheritance", "Properties", "Dataclasses"], { practice: "Python" }),
      step("Data Structures in Python", "Choose the right built-in structure.", ["list, dict, set, tuple", "collections module", "Time complexity", "Generators"]),
      step("Exception Handling & Files", "Handle errors and I/O reliably.", ["try/except/finally", "Custom exceptions", "Context managers", "File handling"], { practice: "Python" }),
      step("Django", "Build full web applications with Django.", ["Models and ORM", "Views and URLs", "Admin", "Migrations"], { practice: "Python" }),
      step("REST APIs", "Expose data with Django REST Framework or FastAPI.", ["Serializers", "Validation", "Authentication", "Status codes"]),
      step("SQL", "Query data behind your applications.", ["Joins", "Aggregations", "ORM vs raw SQL", "Indexes"]),
      step("Testing with pytest", "Test code with confidence.", ["Fixtures", "Parametrised tests", "Mocking", "Coverage"], { practice: null })
    ],
    practiceTopics: ["Python", "REST APIs", "DBMS", "Git"],
    interviewFocus: ["Python", "OOP", "Data structures", "Django", "REST APIs", "SQL", "Project explanation"],
    recommendedProjects: [
      project("Blog Platform", ["Django", "SQL", "Bootstrap"], "Beginner", "Models, ORM queries, authentication and admin."),
      project("Task API", ["FastAPI", "PostgreSQL", "pytest"], "Intermediate", "Typed APIs, validation and automated tests."),
      project("Web Scraper & Report", ["Python", "Requests", "Pandas"], "Intermediate", "Automation, data cleaning and scheduled reporting.")
    ]
  },

  "dotnet-developer": {
    title: ".NET Developer",
    icon: FiCode,
    category: "Software Development",
    domain: "development",
    cardDescription: "C#, ASP.NET Core, APIs, SQL Server and backend development.",
    description: "Prepare C#, ASP.NET Core, Entity Framework and SQL Server development.",
    overview: ".NET developers build enterprise APIs and web applications with C# and ASP.NET Core. Interviews cover C# language features, dependency injection, Entity Framework Core, SQL Server and clean architecture.",
    level: "Beginner to Intermediate",
    focus: "Enterprise Backend",
    skills: ["C#", "OOP", "LINQ", "ASP.NET Core", "Entity Framework Core", "SQL Server", "REST APIs", "Dependency Injection", "Unit Testing"],
    roadmap: [
      step("C# Fundamentals", "Write correct, modern C#.", ["Value vs reference types", "Properties", "Nullable types", "Generics"]),
      step("C# OOP", "Design classes and interfaces in C#.", ["Interfaces", "Abstract classes", "Inheritance", "SOLID principles"]),
      step("LINQ & Collections", "Query in-memory data expressively.", ["List and Dictionary", "LINQ queries", "Deferred execution", "IEnumerable vs IQueryable"]),
      step("Async in C#", "Write non-blocking server code.", ["async/await", "Task", "Cancellation tokens", "Deadlock pitfalls"]),
      step("ASP.NET Core Web API", "Build REST services.", ["Controllers and routing", "Middleware pipeline", "Model validation", "Status codes"], { practice: "REST APIs" }),
      step("Dependency Injection", "Wire services with the built-in container.", ["Service lifetimes", "Constructor injection", "Configuration", "Options pattern"]),
      step("Entity Framework Core", "Map entities to SQL Server.", ["DbContext", "Migrations", "Relationships", "Tracking vs no-tracking"]),
      step("SQL Server", "Write and tune T-SQL queries.", ["Joins", "Stored procedures", "Indexes", "Transactions"]),
      step("Authentication in ASP.NET Core", "Secure APIs with identity and JWT.", ["ASP.NET Identity", "JWT bearer", "Policies", "Claims"])
    ],
    practiceTopics: ["REST APIs", "DBMS", "Git"],
    interviewFocus: ["C# and OOP", "LINQ", "Async/await", "ASP.NET Core", "Entity Framework", "SQL", "Project explanation"],
    recommendedProjects: [
      project("Employee Management API", ["ASP.NET Core", "EF Core", "SQL Server"], "Beginner", "CRUD, migrations, validation and layered services."),
      project("Library System", ["ASP.NET Core MVC", "SQL Server", "Identity"], "Intermediate", "Authentication, roles and relational data."),
      project("Invoice Generator", ["C#", "Web API", "PDF export"], "Intermediate", "Business rules, async processing and document generation.")
    ]
  },

  "nodejs-developer": {
    title: "Node.js Developer",
    icon: FiServer,
    category: "Software Development",
    domain: "development",
    cardDescription: "JavaScript, Node.js, Express, REST APIs and databases.",
    description: "Prepare JavaScript backend development with Node.js and Express.",
    overview: "Node.js developers build fast, event-driven APIs and real-time services in JavaScript. Interviews focus on the event loop, async patterns, Express middleware, databases and handling errors in production.",
    level: "Beginner to Intermediate",
    focus: "JavaScript Backend",
    skills: ["JavaScript", "Node.js", "Express", "REST APIs", "MongoDB", "SQL", "JWT", "npm", "Testing"],
    roadmap: [
      step("JavaScript Fundamentals", "Master the language Node runs.", ["Closures", "Prototypes", "Modules", "Array methods"]),
      step("Node.js Event Loop", "Understand non-blocking I/O.", ["Event loop phases", "Callbacks vs promises", "Blocking code", "Streams"], { practice: "JavaScript" }),
      step("Express", "Build APIs with routing and middleware.", ["Routes", "Middleware chain", "Error middleware", "Request validation"], { practice: "REST APIs" }),
      step("REST APIs", "Design clean endpoints.", ["Resource design", "Status codes", "Pagination", "Versioning"]),
      step("MongoDB", "Model document data.", ["Documents and collections", "Mongoose schemas", "Indexes", "Aggregation pipeline"], { practice: null }),
      step("SQL with Node", "Use relational databases from Node.", ["Query builders/ORMs", "Joins", "Transactions", "Connection pooling"]),
      step("Authentication with JWT", "Secure routes and users.", ["bcrypt", "JWT", "Refresh tokens", "Protected middleware"], { practice: "Spring Security" }),
      step("Testing Node APIs", "Test routes and services.", ["Jest", "Supertest", "Mocking", "Test databases"], { practice: null })
    ],
    practiceTopics: ["JavaScript", "REST APIs", "Git"],
    interviewFocus: ["JavaScript", "Event loop", "Express middleware", "REST APIs", "Databases", "Authentication", "Project explanation"],
    recommendedProjects: [
      project("Social Media API", ["Node.js", "Express", "MongoDB"], "Intermediate", "Relationships, feeds, pagination and authentication."),
      project("Real-time Chat", ["Node.js", "Socket.IO", "Redis"], "Intermediate", "WebSockets, rooms and scaling with a pub/sub store."),
      project("File Upload Service", ["Express", "Streams", "Cloud storage"], "Advanced", "Streaming uploads, validation and storage integration.")
    ]
  },

  "mobile-developer": {
    title: "Mobile App Developer",
    icon: FiSmartphone,
    category: "Software Development",
    domain: "development",
    cardDescription: "Mobile development concepts, APIs, UI and application architecture.",
    description: "Prepare mobile UI, state, networking and app architecture for Android or cross-platform apps.",
    overview: "Mobile developers build apps that feel fast on limited devices and unreliable networks. Interviews cover a platform (Android/Kotlin, Flutter or React Native), app lifecycle, UI state, networking, offline storage and architecture.",
    level: "Beginner to Intermediate",
    focus: "Android / Cross-platform",
    skills: ["Kotlin or Dart", "React Native or Flutter", "UI Layouts", "State Management", "REST APIs", "Local Storage", "App Lifecycle", "Git"],
    roadmap: [
      step("Programming Language", "Get fluent in Kotlin, Dart or JavaScript.", ["Null safety", "Classes", "Collections", "Async code"]),
      step("Mobile UI Layouts", "Build responsive screens.", ["Layouts and constraints", "Lists and recycling", "Navigation", "Theming"]),
      step("App Lifecycle", "Handle how apps start, pause and resume.", ["Activity/screen lifecycle", "Configuration changes", "Background limits", "Permissions"]),
      step("State Management", "Keep UI and data in sync.", ["ViewModel / Provider / Redux", "Unidirectional data flow", "Side effects", "Loading states"]),
      step("Networking & REST APIs", "Fetch and send data safely.", ["HTTP clients", "JSON parsing", "Error and retry handling", "Auth tokens"], { practice: "REST APIs" }),
      step("Local Storage & Offline", "Work without a reliable network.", ["SQLite/Room", "Key-value storage", "Caching", "Sync strategy"], { practice: "DBMS" }),
      step("Mobile Architecture", "Structure code for testability.", ["MVVM", "Repository pattern", "Dependency injection", "Modularisation"], { practice: null }),
      step("Publishing & Testing", "Ship reliable apps to stores.", ["Unit and UI tests", "Build variants", "Store release process", "Crash reporting"], { practice: null })
    ],
    practiceTopics: ["REST APIs", "JavaScript", "Git"],
    interviewFocus: ["App lifecycle", "State management", "REST APIs", "Offline storage", "Architecture", "Project explanation"],
    recommendedProjects: [
      project("Weather App", ["Kotlin or Flutter", "REST API"], "Beginner", "Networking, JSON parsing and loading/error states."),
      project("Expense Tracker App", ["Room/SQLite", "MVVM"], "Intermediate", "Local persistence, charts and clean architecture."),
      project("Food Ordering App", ["React Native", "REST APIs", "Auth"], "Advanced", "Navigation, cart state, authentication and payments flow (mocked).")
    ]
  },

  "software-engineer": {
    title: "Software Engineer",
    icon: FiCode,
    category: "Software Development",
    domain: "development",
    cardDescription: "Programming, DSA, DBMS, OS, networking and system design.",
    description: "Prepare programming fundamentals, DSA and core computer science.",
    overview: "Software engineer interviews — especially at product companies — focus on problem solving with data structures and algorithms, core computer science subjects and, for experienced roles, system design.",
    level: "Intermediate",
    focus: "DSA + CS Fundamentals",
    skills: ["Programming", "Data Structures", "Algorithms", "DBMS", "Operating Systems", "Computer Networks", "OOP", "Git", "System Design"],
    roadmap: [
      step("Programming Fundamentals", "Be fluent in one language for interviews.", ["Syntax and idioms", "Recursion", "Complexity analysis", "Standard library"], { practice: "Java" }),
      step("Data Structures", "Know when to use each structure.", ["Arrays and strings", "Hash maps", "Stacks and queues", "Trees and graphs"]),
      step("Algorithms", "Solve problems with known patterns.", ["Two pointers", "Binary search", "BFS/DFS", "Dynamic programming"]),
      step("Object-Oriented Design", "Design classes for real scenarios.", ["SOLID", "Design patterns", "UML basics", "Low-level design"], { practice: "Java" }),
      step("DBMS", "Explain how databases store and protect data.", ["Normalisation", "Transactions and ACID", "Indexing", "SQL queries"]),
      step("Operating Systems", "Understand processes and memory.", ["Processes vs threads", "Scheduling", "Deadlocks", "Paging"]),
      step("Computer Networks", "Explain how data moves across networks.", ["OSI/TCP-IP", "TCP vs UDP", "HTTP/HTTPS", "DNS"]),
      step("System Design", "Design scalable systems.", ["Load balancing", "Caching", "Database scaling", "Trade-offs"])
    ],
    practiceTopics: ["Java", "DBMS", "Operating Systems", "Computer Networks"],
    interviewFocus: ["Data structures", "Algorithms", "OOP", "DBMS", "Operating Systems", "Computer Networks", "System design"],
    recommendedProjects: [
      project("Library Management (LLD)", ["Java", "OOP", "Design patterns"], "Intermediate", "Low-level design, SOLID principles and class modelling."),
      project("Mini Search Engine", ["Data structures", "Tries", "Ranking"], "Advanced", "Indexing, efficient lookups and algorithmic thinking."),
      project("Rate Limiter Service", ["REST APIs", "Redis", "Algorithms"], "Advanced", "Token bucket algorithm, concurrency and system design trade-offs.")
    ]
  },


  /* ---------- DATA & AI ---------- */

  "data-analyst": {
    title: "Data Analyst",
    icon: FiBarChart2,
    category: "Data & AI",
    domain: "data",
    cardDescription: "SQL, Excel, Python, statistics and visualization.",
    description: "Prepare SQL, analytics, visualization and statistics skills.",
    overview: "Data analysts turn raw data into decisions. Interviews test SQL heavily, then Excel, statistics, a BI tool and — above all — how you frame a business question and communicate findings.",
    level: "Beginner to Intermediate",
    focus: "SQL + Insights",
    skills: ["SQL", "Excel", "Python", "Pandas", "Statistics", "Data Cleaning", "Visualization", "Power BI", "Storytelling"],
    roadmap: [
      step("Excel for Analysis", "Analyse data quickly in spreadsheets.", ["Lookups (XLOOKUP/VLOOKUP)", "Pivot tables", "Conditional formulas", "Charts"], { practice: null }),
      step("SQL Fundamentals", "Pull exactly the data you need.", ["SELECT and WHERE", "Joins", "GROUP BY", "CASE expressions"]),
      step("Advanced SQL", "Answer complex business questions.", ["Subqueries", "CTEs", "Window functions", "Date functions"]),
      step("Python & Pandas", "Clean and analyse larger datasets.", ["DataFrames", "groupby", "merge/join", "Handling missing values"], { practice: "Python" }),
      step("Data Cleaning", "Make data trustworthy before analysis.", ["Duplicates", "Missing values", "Outliers", "Consistent formats"], { practice: null }),
      step("Statistics", "Draw valid conclusions from data.", ["Mean, median, variance", "Distributions", "Hypothesis testing", "A/B tests"], { practice: null }),
      step("Data Visualization", "Show insights clearly.", ["Choosing chart types", "Dashboards", "Avoiding misleading charts", "Colour and clarity"], { practice: null }),
      step("Power BI", "Build interactive business dashboards.", ["Data model", "DAX basics", "Filters and slicers", "Publishing"], { practice: null })
    ],
    practiceTopics: ["DBMS", "Python"],
    interviewFocus: ["SQL", "Window functions", "Excel", "Statistics", "Dashboards", "Business case questions", "Project explanation"],
    recommendedProjects: [
      project("Sales Performance Dashboard", ["SQL", "Power BI"], "Beginner", "KPIs, trends by region/product and interactive filters."),
      project("Customer Churn Analysis", ["Python", "Pandas", "Statistics"], "Intermediate", "Cleaning, cohort analysis and actionable recommendations."),
      project("HR Attrition Report", ["SQL", "Excel", "Visualization"], "Beginner", "Business framing, segmentation and storytelling.")
    ]
  },

  "data-scientist": {
    title: "Data Scientist",
    icon: FiActivity,
    category: "Data & AI",
    domain: "data",
    cardDescription: "Python, statistics, machine learning and data analysis.",
    description: "Prepare statistics, machine learning, experimentation and Python for data science roles.",
    overview: "Data scientists build models and experiments that answer business questions. Interviews cover statistics and probability, ML algorithms and evaluation, Python/SQL skills and case studies about real decisions.",
    level: "Intermediate",
    focus: "Statistics + ML",
    skills: ["Python", "Pandas", "NumPy", "SQL", "Statistics", "Probability", "Machine Learning", "scikit-learn", "Visualization", "Experimentation"],
    roadmap: [
      step("Python for Data Science", "Work efficiently with data in Python.", ["NumPy arrays", "Pandas", "Vectorisation", "Jupyter workflow"], { practice: "Python" }),
      step("SQL for Analysis", "Extract and shape data for modelling.", ["Joins", "Aggregations", "Window functions", "CTEs"]),
      step("Statistics", "Reason about uncertainty.", ["Distributions", "Sampling", "Confidence intervals", "Hypothesis tests"]),
      step("Probability", "Solve probability questions confidently.", ["Conditional probability", "Bayes' theorem", "Expected value", "Common distributions"], { practice: null }),
      step("Exploratory Data Analysis", "Understand data before modelling.", ["Univariate/bivariate analysis", "Correlation", "Outliers", "Feature ideas"], { practice: null }),
      step("Machine Learning Fundamentals", "Build and evaluate models.", ["Regression", "Classification", "Overfitting", "Cross-validation"]),
      step("Feature Engineering", "Turn raw data into predictive signals.", ["Encoding categories", "Scaling", "Feature selection", "Leakage"], { practice: null }),
      step("Model Evaluation", "Choose the right metric for the problem.", ["Precision/recall", "ROC-AUC", "RMSE", "Business metrics"], { practice: null }),
      step("Experimentation & A/B Testing", "Measure impact correctly.", ["Experiment design", "Sample size", "Significance", "Pitfalls"], { practice: null })
    ],
    practiceTopics: ["Python", "DBMS"],
    interviewFocus: ["Statistics", "Probability", "Machine learning", "Model evaluation", "SQL", "Python", "Case studies"],
    recommendedProjects: [
      project("House Price Prediction", ["Python", "scikit-learn"], "Beginner", "Regression, feature engineering and error analysis."),
      project("Customer Segmentation", ["Clustering", "Pandas", "Visualization"], "Intermediate", "Unsupervised learning and business interpretation."),
      project("A/B Test Analysis", ["Statistics", "Python", "SQL"], "Intermediate", "Experiment design, significance testing and recommendations.")
    ]
  },

  "data-engineer": {
    title: "Data Engineer",
    icon: FiDatabase,
    category: "Data & AI",
    domain: "data",
    cardDescription: "SQL, Python, ETL pipelines, warehouses and cloud data tools.",
    description: "Prepare SQL, data modelling, pipelines, warehouses and big-data tools.",
    overview: "Data engineers build the pipelines and warehouses that analysts and scientists depend on. Interviews focus on advanced SQL, data modelling, ETL design, Spark and how you keep data reliable at scale.",
    level: "Intermediate",
    focus: "Pipelines + Warehouses",
    skills: ["SQL", "Python", "Data Modelling", "ETL/ELT", "Apache Spark", "Airflow", "Data Warehouses", "Cloud Storage", "Kafka"],
    roadmap: [
      step("Advanced SQL", "Write complex, efficient transformations.", ["Window functions", "CTEs", "Query plans", "Incremental loads"]),
      step("Python for Data Engineering", "Script and automate data movement.", ["File formats (CSV, JSON, Parquet)", "APIs", "Error handling", "Packaging"], { practice: "Python" }),
      step("Data Modelling", "Design analytics-friendly schemas.", ["Star schema", "Facts and dimensions", "Slowly changing dimensions", "Normalisation trade-offs"], { practice: "DBMS" }),
      step("ETL & ELT Pipelines", "Move and transform data reliably.", ["Extract/transform/load", "Idempotency", "Backfills", "Data quality checks"], { practice: null }),
      step("Data Warehouses", "Store data for analytics at scale.", ["Snowflake/BigQuery/Redshift", "Partitioning", "Clustering", "Cost control"], { practice: null }),
      step("Apache Spark", "Process large datasets in parallel.", ["RDDs vs DataFrames", "Transformations vs actions", "Partitions", "Shuffles"], { practice: null }),
      step("Workflow Orchestration", "Schedule and monitor pipelines.", ["Airflow DAGs", "Dependencies", "Retries", "SLAs"], { practice: null }),
      step("Streaming with Kafka", "Handle real-time data.", ["Topics and partitions", "Consumer groups", "Offsets", "Exactly-once trade-offs"], { practice: null })
    ],
    practiceTopics: ["DBMS", "Python"],
    interviewFocus: ["Advanced SQL", "Data modelling", "ETL design", "Spark", "Data quality", "Pipeline system design"],
    recommendedProjects: [
      project("Batch ETL Pipeline", ["Python", "Airflow", "PostgreSQL"], "Intermediate", "Scheduled ingestion, transformations and data quality checks."),
      project("Analytics Warehouse", ["SQL", "Star schema", "BigQuery"], "Intermediate", "Dimensional modelling and analytics-ready tables."),
      project("Real-time Event Pipeline", ["Kafka", "Spark Streaming"], "Advanced", "Streaming ingestion, windowed aggregations and fault tolerance.")
    ]
  },

  "machine-learning-engineer": {
    title: "Machine Learning Engineer",
    icon: FiCpu,
    category: "Data & AI",
    domain: "data",
    cardDescription: "ML algorithms, Python, model deployment and MLOps.",
    description: "Prepare ML algorithms, deep learning basics, model serving and MLOps.",
    overview: "ML engineers take models from notebooks to production. Interviews combine ML theory, coding, and engineering topics such as feature pipelines, model serving, monitoring and retraining.",
    level: "Intermediate to Advanced",
    focus: "Models in Production",
    skills: ["Python", "Machine Learning", "Deep Learning", "PyTorch or TensorFlow", "Feature Engineering", "Model Serving", "Docker", "MLOps", "SQL"],
    roadmap: [
      step("Python & Coding", "Write production-quality Python.", ["Clean functions", "Data structures", "Testing", "Packaging"], { practice: "Python" }),
      step("Machine Learning Fundamentals", "Know core algorithms deeply.", ["Linear/logistic regression", "Decision trees and ensembles", "Bias-variance", "Regularisation"]),
      step("Deep Learning", "Train neural networks.", ["Backpropagation", "CNNs and RNNs", "Optimisers", "Overfitting controls"], { practice: null }),
      step("Feature Pipelines", "Produce consistent features for training and serving.", ["Feature stores", "Training/serving skew", "Data validation", "Versioning"], { practice: null }),
      step("Model Evaluation", "Measure models against real goals.", ["Offline metrics", "Validation strategy", "Error analysis", "Fairness checks"], { practice: null }),
      step("Model Serving", "Expose models through APIs.", ["REST inference APIs", "Batch vs online", "Latency", "Model versioning"], { practice: "REST APIs" }),
      step("Docker for ML", "Package models reproducibly.", ["Dockerfile", "Dependencies", "GPU images", "Compose"], { practice: "Docker" }),
      step("MLOps & Monitoring", "Keep models healthy after release.", ["Experiment tracking", "CI/CD for models", "Drift detection", "Retraining"], { practice: null })
    ],
    practiceTopics: ["Python", "Docker", "REST APIs"],
    interviewFocus: ["ML algorithms", "Model evaluation", "Deep learning", "Python coding", "Model deployment", "ML system design"],
    recommendedProjects: [
      project("Model Serving API", ["Python", "FastAPI", "Docker"], "Intermediate", "Packaging a trained model behind a versioned inference API."),
      project("Image Classifier", ["PyTorch", "CNN"], "Intermediate", "Training, augmentation and evaluation on real images."),
      project("End-to-end ML Pipeline", ["MLflow", "Airflow", "Docker"], "Advanced", "Experiment tracking, automated retraining and monitoring.")
    ]
  },

  "ai-engineer": {
    title: "AI Engineer",
    icon: FiZap,
    category: "Data & AI",
    domain: "data",
    cardDescription: "AI systems, LLMs, APIs, Python and model integration.",
    description: "Prepare LLM application development, prompt design, retrieval and AI integration.",
    overview: "AI engineers build products on top of large language models and other AI services. Interviews cover Python and APIs, prompt design, retrieval-augmented generation, evaluation, safety and cost/latency trade-offs.",
    level: "Intermediate",
    focus: "LLM Applications",
    skills: ["Python", "REST APIs", "LLM APIs", "Prompt Engineering", "Embeddings", "Vector Databases", "RAG", "Evaluation", "AI Safety"],
    roadmap: [
      step("Python & APIs", "Integrate external services reliably.", ["HTTP clients", "Async calls", "Retries and timeouts", "Secrets management"], { practice: "Python" }),
      step("LLM Fundamentals", "Understand what models can and cannot do.", ["Tokens and context windows", "Temperature", "Hallucinations", "Model selection"], { practice: null }),
      step("Prompt Engineering", "Get reliable, structured outputs.", ["System prompts", "Few-shot examples", "Structured output", "Prompt iteration"], { practice: null }),
      step("Embeddings & Vector Search", "Find relevant content by meaning.", ["Embeddings", "Similarity search", "Chunking", "Vector databases"], { practice: null }),
      step("Retrieval-Augmented Generation", "Ground answers in your own data.", ["Retrieval pipeline", "Context assembly", "Citations", "Freshness"], { practice: null }),
      step("Tool Use & Agents", "Let models call functions safely.", ["Function calling", "Tool schemas", "Agent loops", "Guardrails"], { practice: null }),
      step("Evaluation", "Measure AI quality systematically.", ["Test sets", "Automated grading", "Human review", "Regression tracking"], { practice: null }),
      step("AI Safety & Security", "Protect users and systems.", ["Prompt injection", "Data privacy", "Output filtering", "Keeping API keys server-side"], { practice: null })
    ],
    practiceTopics: ["Python", "REST APIs"],
    interviewFocus: ["LLM fundamentals", "Prompt design", "RAG architecture", "Evaluation", "AI safety", "Python", "Project explanation"],
    recommendedProjects: [
      project("Document Q&A Assistant", ["Python", "Embeddings", "Vector DB"], "Intermediate", "RAG pipeline with citations and evaluation."),
      project("Resume Feedback Tool", ["LLM API", "REST APIs", "React"], "Intermediate", "Structured outputs, server-side keys and user-facing UX."),
      project("Support Agent with Tools", ["Function calling", "Python"], "Advanced", "Tool use, guardrails and conversation state.")
    ]
  },


  /* ---------- CLOUD & DEVOPS ---------- */

  "devops-engineer": {
    title: "DevOps Engineer",
    icon: FiSettings,
    category: "Cloud & DevOps",
    domain: "cloud",
    cardDescription: "CI/CD, Docker, Linux, cloud and automation.",
    description: "Prepare Linux, CI/CD, containers, Kubernetes, infrastructure as code and monitoring.",
    overview: "DevOps engineers automate how software is built, tested, deployed and operated. Interviews cover Linux, scripting, CI/CD pipelines, Docker and Kubernetes, infrastructure as code and troubleshooting scenarios.",
    level: "Intermediate",
    focus: "Automation + Delivery",
    skills: ["Linux", "Bash", "Git", "CI/CD", "Docker", "Kubernetes", "Terraform", "AWS", "Monitoring"],
    roadmap: [
      step("Linux Fundamentals", "Operate servers confidently.", ["File permissions", "Processes and services", "Logs", "Package management"], { practice: "Operating Systems" }),
      step("Bash Scripting", "Automate repetitive tasks.", ["Variables and loops", "Exit codes", "Text processing (grep, awk)", "Cron jobs"], { practice: null }),
      step("Git Workflows", "Support team branching strategies.", ["Branching models", "Tags and releases", "Hooks", "Merge strategies"], { practice: "Git" }),
      step("Networking Fundamentals", "Debug connectivity issues.", ["DNS", "Ports and firewalls", "Load balancers", "TLS"], { practice: "Computer Networks" }),
      step("CI/CD Pipelines", "Automate build, test and deploy.", ["Pipeline stages", "Artifacts", "Secrets", "Deployment strategies"], { practice: null }),
      step("Docker & Containers", "Package applications consistently.", ["Dockerfile", "Image layers", "Registries", "Compose"], { practice: "Docker" }),
      step("Kubernetes", "Run containers at scale.", ["Pods and Deployments", "Services", "ConfigMaps and Secrets", "Scaling"], { practice: null }),
      step("Infrastructure as Code", "Provision infrastructure repeatably.", ["Terraform basics", "State", "Modules", "Plan/apply workflow"], { practice: null }),
      step("Monitoring & Logging", "Know when something breaks and why.", ["Metrics", "Alerts", "Centralised logs", "Dashboards"], { practice: null })
    ],
    practiceTopics: ["Docker", "Git", "Operating Systems", "Computer Networks"],
    interviewFocus: ["Linux", "CI/CD", "Docker", "Kubernetes", "Terraform", "Troubleshooting scenarios", "Project explanation"],
    recommendedProjects: [
      project("CI/CD for a Web App", ["GitHub Actions", "Docker", "AWS"], "Intermediate", "Automated tests, image builds and deployments on merge."),
      project("Kubernetes Deployment", ["Kubernetes", "Helm", "Ingress"], "Advanced", "Deployments, services, config and rolling updates."),
      project("Infrastructure with Terraform", ["Terraform", "AWS"], "Intermediate", "Reproducible VPC, compute and database provisioning.")
    ]
  },

  "cloud-engineer": {
    title: "Cloud Engineer",
    icon: FiCloud,
    category: "Cloud & DevOps",
    domain: "cloud",
    cardDescription: "Cloud architecture, networking, storage, compute and security.",
    description: "Prepare cloud compute, networking, storage, identity and architecture on AWS, Azure or GCP.",
    overview: "Cloud engineers design and operate infrastructure on public cloud platforms. Interviews test core services, networking, identity and security, high availability and cost-aware architecture.",
    level: "Intermediate",
    focus: "AWS / Azure / GCP",
    skills: ["Cloud Fundamentals", "Compute", "Networking (VPC)", "Storage", "IAM", "Databases", "Terraform", "Linux", "Cost Management"],
    roadmap: [
      step("Cloud Fundamentals", "Understand cloud service and pricing models.", ["IaaS, PaaS, SaaS", "Regions and availability zones", "Shared responsibility", "Pricing basics"], { practice: null }),
      step("Linux & Scripting", "Manage cloud servers.", ["SSH", "Permissions", "Services", "Shell scripts"], { practice: "Operating Systems" }),
      step("Compute Services", "Run workloads on VMs, containers and serverless.", ["EC2/VMs", "Auto scaling", "Containers", "Serverless functions"], { practice: null }),
      step("Cloud Networking", "Design secure networks.", ["VPC and subnets", "Route tables", "Security groups", "Load balancers"], { practice: "Computer Networks" }),
      step("Storage & Databases", "Choose the right storage for each need.", ["Object storage", "Block vs file storage", "Managed SQL", "NoSQL options"], { practice: "DBMS" }),
      step("Identity & Access Management", "Grant least-privilege access.", ["Users, roles and policies", "Least privilege", "MFA", "Service roles"], { practice: null }),
      step("Infrastructure as Code", "Provision resources reproducibly.", ["Terraform", "CloudFormation/Bicep", "State", "Modules"], { practice: null }),
      step("High Availability & Cost", "Design reliable, affordable systems.", ["Multi-AZ design", "Backups and DR", "Right-sizing", "Cost alerts"], { practice: null })
    ],
    practiceTopics: ["Computer Networks", "Operating Systems", "Docker"],
    interviewFocus: ["Core cloud services", "VPC networking", "IAM", "High availability", "Infrastructure as code", "Cost optimisation"],
    recommendedProjects: [
      project("Highly Available Web App", ["AWS", "Load Balancer", "Auto Scaling"], "Intermediate", "Multi-AZ architecture, health checks and scaling."),
      project("Static Site + CDN", ["Object storage", "CDN", "DNS"], "Beginner", "Hosting, TLS, caching and DNS configuration."),
      project("Serverless API", ["Lambda/Functions", "API Gateway", "NoSQL"], "Intermediate", "Event-driven compute, IAM roles and pay-per-use design.")
    ]
  },

  "site-reliability-engineer": {
    title: "Site Reliability Engineer",
    icon: FiActivity,
    category: "Cloud & DevOps",
    domain: "cloud",
    cardDescription: "Reliability, monitoring, automation and distributed systems.",
    description: "Prepare reliability engineering, observability, incident response and distributed systems.",
    overview: "SREs keep production systems reliable using software engineering. Interviews cover Linux and networking depth, coding for automation, SLOs and error budgets, observability, incident response and distributed systems design.",
    level: "Intermediate to Advanced",
    focus: "Reliability + Observability",
    skills: ["Linux", "Networking", "Python or Go", "SLIs/SLOs", "Monitoring", "Incident Response", "Kubernetes", "Distributed Systems", "Automation"],
    roadmap: [
      step("Linux Internals", "Troubleshoot servers under pressure.", ["Processes and signals", "Memory and CPU", "File descriptors", "Performance tools"], { practice: "Operating Systems" }),
      step("Networking Deep Dive", "Debug network-level failures.", ["TCP handshake", "DNS resolution", "Load balancing", "Latency"], { practice: "Computer Networks" }),
      step("Coding for Automation", "Replace toil with code.", ["Python/Go scripting", "APIs", "Idempotent scripts", "Testing tools"], { practice: "Python" }),
      step("SLIs, SLOs & Error Budgets", "Define and measure reliability.", ["SLIs", "SLO targets", "Error budgets", "Alerting on symptoms"], { practice: null }),
      step("Observability", "Understand system behaviour in production.", ["Metrics", "Logs", "Traces", "Dashboards"], { practice: null }),
      step("Incident Response", "Restore service and learn from failures.", ["On-call", "Incident roles", "Mitigation first", "Blameless postmortems"], { practice: null }),
      step("Kubernetes Operations", "Run and debug clusters.", ["Probes", "Resource limits", "Rollouts", "Autoscaling"], { practice: null }),
      step("Distributed Systems", "Design for failure.", ["Replication", "Consensus basics", "Timeouts and retries", "Graceful degradation"])
    ],
    practiceTopics: ["Operating Systems", "Computer Networks", "Python", "Docker"],
    interviewFocus: ["Linux troubleshooting", "Networking", "SLOs and error budgets", "Observability", "Incident response", "System design"],
    recommendedProjects: [
      project("Monitoring Stack", ["Prometheus", "Grafana", "Alertmanager"], "Intermediate", "Metrics, SLO dashboards and symptom-based alerts."),
      project("Chaos Testing Lab", ["Kubernetes", "Fault injection"], "Advanced", "Resilience testing and documented failure modes."),
      project("Incident Runbook Automation", ["Python", "APIs"], "Intermediate", "Automating diagnosis and mitigation steps.")
    ]
  },


  /* ---------- CYBERSECURITY ---------- */

  "cybersecurity-analyst": {
    title: "Cybersecurity Analyst",
    icon: FiShield,
    category: "Cybersecurity",
    domain: "security",
    cardDescription: "Security fundamentals, networks, vulnerabilities and incident response.",
    description: "Prepare security fundamentals, networking, vulnerability management and incident response.",
    overview: "Cybersecurity analysts protect an organisation's systems by monitoring, assessing risk and responding to incidents. Interviews test security fundamentals, networking, common attacks, vulnerability management and calm incident handling.",
    level: "Beginner to Intermediate",
    focus: "Defend + Respond",
    skills: ["Networking", "Security Fundamentals", "Linux", "Windows", "Vulnerability Management", "SIEM", "Incident Response", "Risk Assessment"],
    roadmap: [
      step("Networking Fundamentals", "Understand how traffic flows and can be attacked.", ["OSI/TCP-IP", "Ports and protocols", "Firewalls", "DNS"]),
      step("Security Fundamentals", "Explain core security principles.", ["CIA triad", "Authentication vs authorization", "Least privilege", "Defence in depth"], { practice: null }),
      step("Operating System Security", "Harden Linux and Windows systems.", ["Users and permissions", "Patching", "Logging", "Hardening baselines"], { practice: "Operating Systems" }),
      step("Common Attacks", "Recognise how attackers operate.", ["Phishing", "Malware", "Brute force", "Web attacks (SQLi, XSS)"], { practice: null }),
      step("Vulnerability Management", "Find and prioritise weaknesses.", ["Scanning", "CVSS scoring", "Patch prioritisation", "Remediation tracking"], { practice: null }),
      step("SIEM & Log Analysis", "Detect suspicious activity in logs.", ["Log sources", "Correlation rules", "Alert triage", "False positives"], { practice: null }),
      step("Incident Response", "Contain and recover from incidents.", ["Preparation", "Containment", "Eradication and recovery", "Lessons learned"], { practice: null }),
      step("Risk & Compliance", "Connect security to business risk.", ["Risk assessment", "Policies", "ISO 27001 / NIST basics", "Security awareness"], { practice: null })
    ],
    practiceTopics: ["Computer Networks", "Operating Systems"],
    interviewFocus: ["Networking", "CIA triad", "Common attacks", "Vulnerability management", "Incident response", "Scenario questions"],
    recommendedProjects: [
      project("Home Security Lab", ["VirtualBox", "Linux", "Windows"], "Beginner", "Isolated lab setup, hardening and log collection."),
      project("Phishing Analysis Report", ["Email headers", "Sandboxing"], "Beginner", "Indicator extraction and clear incident write-up."),
      project("Vulnerability Assessment (authorised lab)", ["Nessus/OpenVAS", "CVSS"], "Intermediate", "Scanning a lab network, prioritising and reporting fixes.")
    ]
  },

  "soc-analyst": {
    title: "SOC Analyst",
    icon: FiEye,
    category: "Cybersecurity",
    domain: "security",
    cardDescription: "Threat monitoring, SIEM, incident handling and security operations.",
    description: "Prepare security monitoring, alert triage, SIEM queries and incident handling.",
    overview: "SOC analysts monitor alerts around the clock, triage what is real and escalate incidents. Interviews test networking and log knowledge, SIEM usage, MITRE ATT&CK, triage reasoning and clear communication.",
    level: "Beginner to Intermediate",
    focus: "Monitoring + Triage",
    skills: ["Networking", "Log Analysis", "SIEM (Splunk/Sentinel)", "MITRE ATT&CK", "Alert Triage", "Endpoint Detection", "Incident Handling", "Threat Intelligence"],
    roadmap: [
      step("Networking for SOC", "Read network traffic and logs.", ["TCP/IP", "Common ports", "Firewall logs", "Proxy and DNS logs"], { practice: "Computer Networks" }),
      step("Windows & Linux Logs", "Know which events matter.", ["Windows Event IDs", "Authentication logs", "Process creation", "Syslog"], { practice: null }),
      step("SIEM Fundamentals", "Search and correlate events.", ["Log ingestion", "Search queries (SPL/KQL)", "Dashboards", "Correlation rules"], { practice: null }),
      step("Alert Triage", "Separate true threats from noise.", ["True vs false positives", "Severity", "Enrichment", "Escalation criteria"], { practice: null }),
      step("MITRE ATT&CK", "Map activity to attacker techniques.", ["Tactics vs techniques", "Initial access", "Lateral movement", "Persistence"], { practice: null }),
      step("Endpoint Detection & Response", "Investigate suspicious hosts.", ["EDR telemetry", "Process trees", "Isolation", "IOC hunting"], { practice: null }),
      step("Incident Handling", "Follow the incident lifecycle.", ["Playbooks", "Containment", "Evidence preservation", "Ticket documentation"], { practice: null }),
      step("Threat Intelligence", "Use external context in investigations.", ["IOCs", "Reputation lookups", "Threat feeds", "TTPs"], { practice: null })
    ],
    practiceTopics: ["Computer Networks", "Operating Systems"],
    interviewFocus: ["Networking", "Windows event logs", "SIEM queries", "Alert triage", "MITRE ATT&CK", "Incident scenarios"],
    recommendedProjects: [
      project("SIEM Home Lab", ["Splunk or Elastic", "Sysmon"], "Intermediate", "Log ingestion, detection rules and dashboards."),
      project("Brute-force Detection Rule", ["SIEM queries", "Windows logs"], "Beginner", "Writing and tuning a detection with documented logic."),
      project("Incident Playbook", ["MITRE ATT&CK", "Documentation"], "Beginner", "Structured response steps for a phishing incident.")
    ]
  },

  "security-engineer": {
    title: "Security Engineer",
    icon: FiLock,
    category: "Cybersecurity",
    domain: "security",
    cardDescription: "Application security, infrastructure security and secure design.",
    description: "Prepare application security, cloud security, secure design and automation.",
    overview: "Security engineers build security into systems — secure code, hardened infrastructure, identity and automated controls. Interviews combine coding, web/application security, cloud security and threat modelling.",
    level: "Intermediate to Advanced",
    focus: "Secure by Design",
    skills: ["Application Security", "OWASP Top 10", "Cryptography", "Identity & Access", "Cloud Security", "Threat Modelling", "Python", "Secure SDLC"],
    roadmap: [
      step("Security Fundamentals", "Apply core principles to designs.", ["CIA triad", "Least privilege", "Defence in depth", "Zero trust"], { practice: null }),
      step("Web Application Security", "Prevent the most common web flaws.", ["OWASP Top 10", "Injection", "XSS and CSRF", "Broken access control"], { practice: "REST APIs" }),
      step("Authentication & Identity", "Design secure login and access.", ["OAuth 2.0 and OIDC", "JWT pitfalls", "MFA", "Session management"], { practice: "Spring Security" }),
      step("Cryptography Basics", "Use crypto correctly.", ["Hashing vs encryption", "Symmetric vs asymmetric", "TLS", "Key management"], { practice: null }),
      step("Cloud Security", "Secure cloud workloads.", ["IAM policies", "Network segmentation", "Secrets management", "Logging"], { practice: null }),
      step("Threat Modelling", "Find design flaws early.", ["STRIDE", "Data flow diagrams", "Trust boundaries", "Mitigations"], { practice: null }),
      step("Secure SDLC & Automation", "Shift security left.", ["SAST/DAST", "Dependency scanning", "Code review", "Security in CI/CD"], { practice: null }),
      step("Security Scripting", "Automate checks and response.", ["Python scripting", "APIs", "Log parsing", "Alert automation"], { practice: "Python" })
    ],
    practiceTopics: ["Spring Security", "REST APIs", "Computer Networks", "Python"],
    interviewFocus: ["OWASP Top 10", "Authentication", "Cryptography", "Cloud security", "Threat modelling", "Secure code review"],
    recommendedProjects: [
      project("Secure Login Service", ["Spring Security", "JWT", "BCrypt"], "Intermediate", "Hardened authentication with rate limiting and MFA."),
      project("CI Security Scanner", ["GitHub Actions", "SAST", "Dependency scan"], "Intermediate", "Automated security gates in a pipeline."),
      project("Threat Model of a Web App", ["STRIDE", "DFDs"], "Beginner", "Identifying risks and documenting mitigations.")
    ]
  },

  "penetration-tester": {
    title: "Penetration Tester",
    icon: FiTarget,
    category: "Cybersecurity",
    domain: "security",
    cardDescription: "Web security, networking, vulnerability assessment and reporting.",
    description: "Prepare authorised security testing: methodology, web and network testing, and reporting.",
    overview: "Penetration testers assess systems — with written authorisation and a defined scope — to find weaknesses before attackers do. Interviews cover methodology, networking, web vulnerabilities, Linux, and above all clear, actionable reporting.",
    level: "Intermediate",
    focus: "Authorised Testing",
    skills: ["Networking", "Linux", "Web Security", "OWASP Top 10", "Burp Suite", "Nmap", "Scripting", "Reporting", "Rules of Engagement"],
    roadmap: [
      step("Rules of Engagement", "Test legally and ethically.", ["Written authorisation", "Scope", "Rules of engagement", "Responsible disclosure"], { practice: null }),
      step("Networking for Testers", "Understand what you are testing.", ["TCP/IP", "Ports and services", "Subnets", "Common protocols"], { practice: "Computer Networks" }),
      step("Linux for Testers", "Work efficiently on the command line.", ["File system", "Permissions", "Bash", "Package tools"], { practice: "Operating Systems" }),
      step("Testing Methodology", "Follow a repeatable process.", ["Reconnaissance", "Enumeration", "Vulnerability analysis", "Reporting"], { practice: null }),
      step("Web Application Testing", "Assess web apps systematically.", ["OWASP Top 10", "Authentication flaws", "Injection", "Access control"], { practice: null }),
      step("Tooling", "Use standard tools responsibly in labs.", ["Nmap", "Burp Suite", "Vulnerability scanners", "Wordlists"], { practice: null }),
      step("Scripting for Testers", "Automate repetitive checks.", ["Python", "Bash", "HTTP requests", "Parsing output"], { practice: "Python" }),
      step("Reporting", "Turn findings into fixes.", ["Executive summary", "Severity rating", "Evidence", "Remediation advice"], { practice: null })
    ],
    practiceTopics: ["Computer Networks", "Operating Systems", "Python"],
    interviewFocus: ["Methodology", "Networking", "OWASP Top 10", "Linux", "Reporting", "Ethics and scope"],
    recommendedProjects: [
      project("Intentionally Vulnerable Lab Write-ups", ["DVWA / Juice Shop", "Burp Suite"], "Intermediate", "Methodical testing of a legal practice target with documented findings."),
      project("Sample Pentest Report", ["CVSS", "Documentation"], "Beginner", "Professional reporting with impact and remediation."),
      project("Recon Automation Script", ["Python", "Lab network"], "Intermediate", "Automating authorised enumeration and summarising output.")
    ]
  },


  /* ---------- OTHER TECHNOLOGY ROLES ---------- */

  "qa-engineer": {
    title: "QA Engineer",
    icon: FiCheckSquare,
    category: "Other Technology Roles",
    domain: "quality",
    cardDescription: "Manual testing, test cases, APIs and software-quality concepts.",
    description: "Prepare testing fundamentals, test design, API testing, SQL and defect management.",
    overview: "QA engineers make sure software works as intended before users find problems. Interviews test the SDLC/STLC, test design techniques, writing clear test cases and bug reports, API testing and SQL for data validation.",
    level: "Beginner",
    focus: "Manual + API Testing",
    skills: ["Testing Fundamentals", "SDLC/STLC", "Test Case Design", "Bug Reporting", "API Testing", "Postman", "SQL", "Agile", "Jira"],
    roadmap: [
      step("Testing Fundamentals", "Know what, why and when to test.", ["Verification vs validation", "Test levels", "Functional vs non-functional", "Regression testing"], { practice: null }),
      step("SDLC & STLC", "Fit testing into delivery.", ["SDLC models", "STLC phases", "Entry/exit criteria", "Agile testing"], { practice: null }),
      step("Test Case Design", "Design tests that find bugs.", ["Equivalence partitioning", "Boundary value analysis", "Decision tables", "Negative testing"], { practice: null }),
      step("Defect Management", "Report bugs developers can fix.", ["Bug lifecycle", "Severity vs priority", "Reproduction steps", "Jira workflow"], { practice: null }),
      step("API Testing", "Test services without a UI.", ["HTTP methods and status codes", "Postman collections", "Assertions", "Auth headers"], { practice: "REST APIs" }),
      step("SQL for Testers", "Validate data behind the UI.", ["SELECT and joins", "Data validation queries", "Aggregates", "Test data setup"]),
      step("Agile & Collaboration", "Work inside a scrum team.", ["User stories", "Acceptance criteria", "Sprint ceremonies", "Definition of done"], { practice: null }),
      step("Intro to Automation", "Know when automation pays off.", ["What to automate", "Selenium basics", "Test pyramid", "Maintenance cost"], { practice: null })
    ],
    practiceTopics: ["REST APIs", "DBMS"],
    interviewFocus: ["Testing fundamentals", "Test case design", "Bug reporting", "API testing", "SQL", "Scenario questions"],
    recommendedProjects: [
      project("Test Plan for a Login Page", ["Test cases", "BVA", "Negative tests"], "Beginner", "Structured test design with traceability."),
      project("API Test Collection", ["Postman", "REST APIs"], "Beginner", "Assertions, environments and chained requests."),
      project("Bug Report Portfolio", ["Jira", "Screenshots", "Logs"], "Beginner", "Clear, reproducible defect reports with severity reasoning.")
    ]
  },

  "automation-test-engineer": {
    title: "Automation Test Engineer",
    icon: FiSettings,
    category: "Other Technology Roles",
    domain: "quality",
    cardDescription: "Selenium, automation frameworks, APIs and CI testing.",
    description: "Prepare test automation with a programming language, Selenium, frameworks, API automation and CI.",
    overview: "Automation test engineers build maintainable test suites that run on every change. Interviews cover a language (Java or Python), Selenium and locators, framework design (POM, TestNG/JUnit), API automation and CI integration.",
    level: "Intermediate",
    focus: "Frameworks + CI",
    skills: ["Java or Python", "Selenium WebDriver", "TestNG/JUnit", "Page Object Model", "API Automation", "REST Assured", "Git", "CI/CD", "SQL"],
    roadmap: [
      step("Programming for Automation", "Write clean test code.", ["OOP", "Collections", "Exceptions", "File handling"], { practice: "Java" }),
      step("Selenium WebDriver", "Automate browser interactions.", ["Locators (CSS/XPath)", "Waits", "Actions", "Browser drivers"], { practice: null }),
      step("Test Frameworks", "Organise and run tests.", ["TestNG/JUnit annotations", "Assertions", "Data-driven tests", "Reports"], { practice: null }),
      step("Page Object Model", "Keep UI tests maintainable.", ["Page classes", "Reusable components", "Base classes", "Avoiding duplication"], { practice: null }),
      step("API Automation", "Test services quickly and reliably.", ["REST Assured / requests", "JSON assertions", "Auth", "Schema validation"], { practice: "REST APIs" }),
      step("Git for Testers", "Version and review test code.", ["Branches", "Pull requests", "Code review", "Merge conflicts"], { practice: "Git" }),
      step("CI Integration", "Run suites on every change.", ["Jenkins/GitHub Actions", "Headless browsers", "Parallel runs", "Flaky test handling"], { practice: null }),
      step("Framework Design", "Design a scalable automation framework.", ["Configuration", "Logging", "Test data management", "Reporting"], { practice: null })
    ],
    practiceTopics: ["Java", "REST APIs", "Git"],
    interviewFocus: ["Java/OOP", "Selenium locators and waits", "Page Object Model", "TestNG", "API automation", "Framework explanation"],
    recommendedProjects: [
      project("E-Commerce UI Automation", ["Selenium", "Java", "TestNG", "POM"], "Intermediate", "Maintainable UI suite with data-driven tests."),
      project("API Automation Suite", ["REST Assured", "JUnit"], "Intermediate", "Contract checks, auth flows and reporting."),
      project("CI Test Pipeline", ["GitHub Actions", "Headless Chrome"], "Intermediate", "Automated runs on pull requests with reports.")
    ]
  },

  "business-analyst": {
    title: "Business Analyst",
    icon: FiBriefcase,
    category: "Other Technology Roles",
    domain: "business",
    cardDescription: "Requirements, process analysis, documentation and stakeholder communication.",
    description: "Prepare requirements gathering, process modelling, documentation, SQL and stakeholder communication.",
    overview: "Business analysts bridge business needs and technical teams. Interviews test requirements elicitation, writing user stories and acceptance criteria, process modelling, basic SQL/data skills and stakeholder communication.",
    level: "Beginner to Intermediate",
    focus: "Requirements + Communication",
    skills: ["Requirements Gathering", "User Stories", "Process Modelling", "BPMN/UML", "SQL", "Excel", "Agile", "Stakeholder Management", "Documentation"],
    roadmap: [
      step("Business Analysis Fundamentals", "Understand the BA role and lifecycle.", ["BA responsibilities", "Business vs functional requirements", "Scope", "Stakeholders"], { practice: null }),
      step("Requirements Elicitation", "Gather the right requirements.", ["Interviews", "Workshops", "Observation", "Document analysis"], { practice: null }),
      step("User Stories & Acceptance Criteria", "Write testable requirements.", ["INVEST", "Acceptance criteria", "Given/When/Then", "Backlog refinement"], { practice: null }),
      step("Process Modelling", "Visualise current and future processes.", ["As-is vs to-be", "BPMN", "Flowcharts", "Gap analysis"], { practice: null }),
      step("SQL for Analysts", "Answer questions with data.", ["SELECT and filters", "Joins", "Aggregates", "Reporting queries"]),
      step("Excel & Reporting", "Summarise data for decisions.", ["Pivot tables", "Lookups", "Charts", "KPI reports"], { practice: null }),
      step("Agile & Scrum", "Work with delivery teams.", ["Scrum roles", "Ceremonies", "Prioritisation (MoSCoW)", "Definition of done"], { practice: null }),
      step("Stakeholder Communication", "Align people around decisions.", ["Stakeholder mapping", "Presenting findings", "Handling conflict", "Sign-off"], { practice: null })
    ],
    practiceTopics: ["DBMS"],
    interviewFocus: ["Requirements gathering", "User stories", "Process modelling", "SQL", "Stakeholder scenarios", "Case studies"],
    recommendedProjects: [
      project("Requirements Document", ["BRD", "User stories"], "Beginner", "Clear scope, requirements and acceptance criteria for a sample app."),
      project("Process Improvement Case", ["BPMN", "Gap analysis"], "Intermediate", "As-is/to-be modelling with measurable improvements."),
      project("KPI Dashboard", ["SQL", "Excel/Power BI"], "Beginner", "Translating business questions into metrics and visuals.")
    ]
  }
};

export const trackCategories = [
  "Software Development",
  "Data & AI",
  "Cloud & DevOps",
  "Cybersecurity",
  "Other Technology Roles"
].map((title) => ({
  title,
  tracks: Object.entries(careerTracks)
    .filter(([, track]) => track.category === title)
    .map(([id, track]) => ({ id, ...track }))
}));


/* =====================================================
   LOOKUPS
===================================================== */

export function getTrack(trackId) {
  const track = careerTracks[trackId];
  return track ? { id: trackId, ...track } : null;
}

export function getTrackByTitle(title) {
  const match = Object.entries(careerTracks).find(([, track]) => track.title === title);
  return match ? getTrack(match[0]) : null;
}

export function getTopic(trackId, topicId) {
  const track = getTrack(trackId);
  if (!track) return { track: null, topic: null, index: -1 };

  const index = track.roadmap.findIndex((item) => item.id === topicId);
  return { track, topic: index >= 0 ? track.roadmap[index] : null, index };
}

/** Find a roadmap topic whose title mentions the given skill. */
export function findTopicForSkill(track, skill) {
  const needle = skill.toLowerCase();
  return track.roadmap.find((item) => item.title.toLowerCase().includes(needle)) || null;
}


/* =====================================================
   PRACTICE LINKS
   Only real destinations are returned; null means no link.
===================================================== */

// Order matters: more specific patterns first.
const technicalMatchers = [
  [/spring security/i, "Spring Security"],
  [/spring boot|spring framework|\bjpa\b|hibernate/i, "Spring Boot"],
  [/javascript/i, "JavaScript"],
  [/\breact\b/i, "React"],
  [/\bjava\b/i, "Java"],
  [/python/i, "Python"],
  [/docker/i, "Docker"],
  [/\brest\b|\bapis?\b/i, "REST APIs"],
  [/\bgit\b/i, "Git"],
  [/\bdbms\b|database design/i, "DBMS"],
  [/operating system/i, "Operating Systems"],
  [/computer networks|networking/i, "Computer Networks"]
];

const technicalPath = (topic, type) =>
  `/practice/technical?topic=${encodeURIComponent(topic)}${type ? `&type=${type}` : ""}`;

/**
 * Resolve where a skill or topic label can be practised.
 * override: a technical topic name, or null to force "no practice link".
 */
export function getPracticeLink(label, override) {
  if (override === null) return null;

  if (override && technicalTopics.includes(override)) {
    return { module: "technical", topic: override, path: technicalPath(override), label: `${override} practice` };
  }

  if (/\bsql\b|mysql|t-sql/i.test(label)) {
    return { module: "sql", path: "/practice/sql", label: "SQL practice" };
  }

  if (/data structures|algorithms|\bdsa\b|coding|problem solving/i.test(label)) {
    return { module: "coding", path: "/practice/coding", label: "Coding practice" };
  }

  if (/system design|distributed systems/i.test(label)) {
    return { module: "system-design", path: "/practice/system-design", label: "System design practice" };
  }

  if (/project explanation|communication|stakeholder/i.test(label)) {
    return { module: "voice", path: "/practice/voice", label: "Voice practice" };
  }

  const match = technicalMatchers.find(([pattern]) => pattern.test(label));

  if (match && technicalTopics.includes(match[1])) {
    return { module: "technical", topic: match[1], path: technicalPath(match[1]), label: `${match[1]} practice` };
  }

  return null;
}

export function getTopicPracticeLinks(topic) {
  const link = getPracticeLink(topic.title, topic.practice);

  if (!link) return null;

  if (link.module === "technical") {
    return {
      ...link,
      notes: technicalPath(link.topic, "notes"),
      quiz: technicalPath(link.topic, "quiz")
    };
  }

  return link;
}


/* =====================================================
   TOPIC DETAIL CONTENT
   Composed from roadmap data + topic library.
   TODO backend: GET /api/career-tracks/{trackId}/topics/{topicId}
===================================================== */

const lowerFirst = (text) => text.charAt(0).toLowerCase() + text.slice(1);

export function buildTopicContent(track, topic) {
  const library = topicLibrary[topic.id] || {};
  const [first, second, third, fourth] = topic.concepts;
  const projectTitle = track.recommendedProjects[0]?.title || "portfolio";

  return {
    know: library.know || `${topic.purpose} Focus on ${first}, ${second}, ${third} and ${fourth}, and be able to explain each with an example.`,
    why: `As a ${track.title}, you will need to ${lowerFirst(topic.purpose).replace(/\.$/, "")}. Interviewers for this role expect you to connect ${topic.title} to real work — ideally a feature from your own projects such as the ${projectTitle}.`,
    concepts: topic.concepts,
    interview: [
      `Explain ${first} with an example from a project.`,
      `How does ${second} work, and when would you use it?`,
      `What problems or trade-offs come with ${third}?`,
      `How have you applied ${fourth} — or how would you — as a ${track.title}?`
    ],
    mistakes: library.mistakes || domainMistakes[track.domain],
    path: [
      `Learn the core ideas: ${first} and ${second}.`,
      `Work through small, focused examples of ${third}.`,
      `Apply ${fourth} in a feature of the ${projectTitle} project.`,
      "Test yourself with practice questions and note weak areas.",
      `Explain ${topic.title} aloud in 2 minutes, as you would in an interview.`
    ]
  };
}


/* =====================================================
   SELECTED TRACK (frontend persistence)
   TODO backend: PUT /api/users/me/target-track
===================================================== */

export function getSelectedTrack() {
  try {
    const saved = JSON.parse(localStorage.getItem(SELECTED_TRACK_KEY) || "null");
    return saved && careerTracks[saved.id] ? saved : null;
  } catch {
    return null;
  }
}

export function saveSelectedTrack(track) {
  const value = { id: track.id, title: track.title, selectedAt: new Date().toISOString() };

  try {
    localStorage.setItem(SELECTED_TRACK_KEY, JSON.stringify(value));
  } catch {
    // Storage unavailable (private mode) — selection simply isn't remembered.
  }

  return value;
}

/** Profile target role wins, then the locally selected track, then Java Full Stack. */
export function resolveActiveTrack(user) {
  return (
    getTrackByTitle(user?.profile?.targetRole) ||
    (getSelectedTrack() && getTrack(getSelectedTrack().id)) ||
    getTrack("java-full-stack")
  );
}


/* =====================================================
   SKILL GAP DEMO DATA
   TODO backend: readiness engine combining
   profile skills + practice scores + assessments + interview results.
===================================================== */

const javaFullStackDemo = [
  { name: "Java", value: 88 },
  { name: "SQL", value: 84 },
  { name: "React", value: 76 },
  { name: "Spring Boot", value: 72 },
  { name: "REST APIs", value: 69 },
  { name: "Spring Security", value: 54 },
  { name: "Docker", value: 42 }
];

// Stable sample value per skill so the demo doesn't change between visits.
function sampleValue(text) {
  let hash = 0;
  for (const char of text) hash = (hash * 31 + char.charCodeAt(0)) % 997;
  return 40 + (hash % 50);
}

export function getSkillStatus(value) {
  if (value >= 80) return "Strong";
  if (value >= 70) return "Good";
  if (value >= 60) return "Improve";
  return "Priority";
}

export function getDemoSkillGap(track) {
  const base = track.id === "java-full-stack"
    ? javaFullStackDemo
    : track.skills.slice(0, 7).map((name) => ({ name, value: sampleValue(`${track.id}:${name}`) }));

  const skills = base
    .map((skill) => ({ ...skill, status: getSkillStatus(skill.value) }))
    .sort((a, b) => b.value - a.value);

  const readiness = track.id === "java-full-stack"
    ? 68
    : Math.round(skills.reduce((sum, skill) => sum + skill.value, 0) / skills.length);

  return { readiness, skills };
}
