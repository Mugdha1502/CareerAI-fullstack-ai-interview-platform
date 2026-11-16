// Demo content for Technical Practice.
// TODO backend: this file is replaced by the CareerAI question/content API.
// Shapes here match what the API is expected to return so the UI does not change.

export const technicalTopics = [
  "Java",
  "Python",
  "JavaScript",
  "React",
  "Spring Boot",
  "DBMS",
  "Operating Systems",
  "Computer Networks",
  "Git",
  "REST APIs",
  "Spring Security",
  "Docker"
];

/* =====================================================
   LEARN / NOTES
   { id, title, concept, explanation, keyPoints[], example, tips[], mistakes[] }
===================================================== */

export const technicalNotes = {
  Java: [
    {
      id: "java-oop",
      title: "OOP",
      concept: "Object-oriented programming organises code around objects that combine state (fields) and behaviour (methods).",
      explanation: "Java is built on four OOP principles. Encapsulation hides internal state behind methods. Inheritance lets a class reuse and extend another class. Polymorphism lets one reference type point to different implementations. Abstraction exposes only what a caller needs through abstract classes and interfaces.",
      keyPoints: [
        "Encapsulation: private fields with public getters/setters or behaviour methods.",
        "Inheritance: a class extends one class but can implement many interfaces.",
        "Polymorphism: method overriding (runtime) and overloading (compile time).",
        "Abstraction: interfaces define a contract; abstract classes share partial implementation."
      ],
      example: `interface Shape {
  double area();
}

class Circle implements Shape {
  private final double radius;

  Circle(double radius) { this.radius = radius; }

  @Override
  public double area() { return Math.PI * radius * radius; }
}

Shape shape = new Circle(2);   // polymorphism
System.out.println(shape.area());`,
      tips: [
        "Explain each principle with a short real example rather than a definition only.",
        "Mention when you would choose an interface over an abstract class."
      ],
      mistakes: [
        "Confusing overloading (same name, different parameters) with overriding.",
        "Saying Java supports multiple inheritance of classes — it does not."
      ]
    },
    {
      id: "java-collections",
      title: "Collections",
      concept: "The Collections Framework provides ready-made data structures such as List, Set, Queue and Map.",
      explanation: "Choose a collection based on ordering, duplicates and lookup speed. ArrayList gives fast index access. LinkedList is efficient for insertions at the ends. HashSet stores unique elements with fast lookup. HashMap stores key–value pairs with unique keys. TreeMap and TreeSet keep elements sorted.",
      keyPoints: [
        "List allows duplicates and preserves insertion order.",
        "Set does not allow duplicate elements.",
        "Map stores key–value pairs; keys are unique, values may repeat.",
        "HashMap lookups are O(1) on average; TreeMap lookups are O(log n)."
      ],
      example: `List<String> names = new ArrayList<>(List.of("Asha", "Ravi", "Asha"));
Set<String> unique = new HashSet<>(names);   // [Asha, Ravi]

Map<String, Integer> scores = new HashMap<>();
scores.put("Asha", 88);
scores.merge("Asha", 5, Integer::sum);       // 93`,
      tips: [
        "Always justify your choice of collection in terms of time complexity.",
        "Know that HashMap is not thread-safe; mention ConcurrentHashMap for concurrency."
      ],
      mistakes: [
        "Modifying a collection while iterating with a for-each loop (ConcurrentModificationException).",
        "Using mutable objects as HashMap keys without proper equals() and hashCode()."
      ]
    },
    {
      id: "java-exceptions",
      title: "Exception Handling",
      concept: "Exceptions signal errors at runtime and let you separate error handling from normal logic.",
      explanation: "Checked exceptions (such as IOException) must be declared or handled. Unchecked exceptions (subclasses of RuntimeException) usually represent programming errors. Use try-catch to handle, finally or try-with-resources to clean up, and custom exceptions to express domain errors.",
      keyPoints: [
        "Checked exceptions are verified at compile time; unchecked are not.",
        "finally runs whether or not an exception occurs.",
        "try-with-resources closes AutoCloseable resources automatically.",
        "Catch the most specific exception first."
      ],
      example: `try (BufferedReader reader = Files.newBufferedReader(path)) {
  return reader.readLine();
} catch (IOException e) {
  throw new ResumeParseException("Could not read resume", e);
}`,
      tips: [
        "Mention that exceptions should not be used for normal control flow.",
        "Show how you wrap low-level exceptions in meaningful custom exceptions."
      ],
      mistakes: [
        "Catching Exception or Throwable and silently ignoring it.",
        "Losing the original cause when re-throwing."
      ]
    },
    {
      id: "java-threads",
      title: "Multithreading",
      concept: "Multithreading runs multiple tasks concurrently within the same process.",
      explanation: "Threads share heap memory, so shared mutable data must be protected. Use synchronized blocks, locks or concurrent collections for safety. In modern Java, prefer ExecutorService and CompletableFuture over creating threads manually.",
      keyPoints: [
        "Create work with Runnable or Callable; run it with an ExecutorService.",
        "synchronized provides mutual exclusion and visibility.",
        "volatile guarantees visibility, not atomicity.",
        "Atomic classes and ConcurrentHashMap avoid explicit locking."
      ],
      example: `ExecutorService pool = Executors.newFixedThreadPool(4);

Future<Integer> result = pool.submit(() -> expensiveCalculation());
System.out.println(result.get());

pool.shutdown();`,
      tips: [
        "Explain race conditions with a simple counter example.",
        "Mention deadlock and how consistent lock ordering prevents it."
      ],
      mistakes: [
        "Assuming volatile makes count++ thread-safe.",
        "Forgetting to shut down an ExecutorService."
      ]
    },
    {
      id: "java-streams",
      title: "Streams",
      concept: "The Stream API processes collections declaratively using a pipeline of operations.",
      explanation: "A stream pipeline has a source, zero or more intermediate operations (map, filter, sorted) and one terminal operation (collect, forEach, reduce). Intermediate operations are lazy and only run when a terminal operation is called.",
      keyPoints: [
        "Intermediate operations return a new stream and are lazy.",
        "Terminal operations produce a result and close the stream.",
        "Streams cannot be reused after a terminal operation.",
        "Collectors.groupingBy is useful for aggregation."
      ],
      example: `List<String> topSkills = candidates.stream()
  .filter(c -> c.score() >= 80)
  .map(Candidate::skill)
  .distinct()
  .sorted()
  .collect(Collectors.toList());`,
      tips: [
        "Be ready to rewrite a simple for-loop as a stream and explain the trade-off.",
        "Mention that parallel streams are not always faster."
      ],
      mistakes: [
        "Calling a terminal operation twice on the same stream.",
        "Using streams with side effects such as modifying external lists."
      ]
    },
    {
      id: "java-jvm",
      title: "JVM Basics",
      concept: "The Java Virtual Machine runs compiled bytecode and manages memory for you.",
      explanation: "javac compiles source code to bytecode. The JVM loads classes, verifies them and executes them using an interpreter and the JIT compiler. Objects live on the heap and are reclaimed by the garbage collector; each thread has its own stack for method calls and local variables.",
      keyPoints: [
        "JDK = development tools + JRE; JRE = JVM + core libraries.",
        "Heap stores objects; stack stores frames and local variables.",
        "Garbage collection frees objects that are no longer reachable.",
        "The JIT compiler optimises frequently executed code."
      ],
      example: `// Compile and run
javac Main.java     // produces Main.class (bytecode)
java Main           // JVM loads and executes the bytecode`,
      tips: [
        "Explain 'write once, run anywhere' in terms of bytecode.",
        "Know the difference between StackOverflowError and OutOfMemoryError."
      ],
      mistakes: [
        "Saying primitives are always stored on the stack (fields of objects live on the heap).",
        "Assuming System.gc() forces garbage collection."
      ]
    }
  ],

  Python: [
    {
      id: "py-collections",
      title: "Lists, Tuples & Dictionaries",
      concept: "Python's built-in collections cover most everyday data-structure needs.",
      explanation: "Lists are ordered and mutable. Tuples are ordered and immutable, so they can be used as dictionary keys. Dictionaries map hashable keys to values with average O(1) lookups. Sets store unique hashable values.",
      keyPoints: [
        "Lists: mutable, ordered, allow duplicates.",
        "Tuples: immutable and hashable if their contents are hashable.",
        "Dictionaries preserve insertion order (Python 3.7+).",
        "Comprehensions create collections concisely."
      ],
      example: `skills = ["java", "sql", "java"]
unique = set(skills)                 # {'java', 'sql'}
point = (3, 4)                       # tuple
counts = {s: skills.count(s) for s in unique}`,
      tips: [
        "Explain why tuples can be dictionary keys but lists cannot.",
        "Mention time complexity of lookups in list vs set."
      ],
      mistakes: [
        "Using a mutable default argument such as def f(items=[]).",
        "Checking membership in a large list instead of a set."
      ]
    },
    {
      id: "py-functions",
      title: "Functions & Decorators",
      concept: "Functions are first-class objects, which enables decorators and higher-order functions.",
      explanation: "A decorator is a function that takes another function and returns a wrapped version that adds behaviour such as logging, caching or access checks, without changing the original code.",
      keyPoints: [
        "*args and **kwargs accept variable arguments.",
        "Decorators wrap functions; use functools.wraps to keep metadata.",
        "Generators use yield to produce values lazily.",
        "lambda creates small anonymous functions."
      ],
      example: `from functools import wraps

def log_calls(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        print(f"Calling {fn.__name__}")
        return fn(*args, **kwargs)
    return wrapper

@log_calls
def greet(name):
    return f"Hello {name}"`,
      tips: [
        "Write a tiny decorator from memory — it is a common interview task.",
        "Explain generators in terms of memory efficiency."
      ],
      mistakes: [
        "Forgetting to return the wrapped function's result.",
        "Calling the decorator instead of passing the function."
      ]
    }
  ],

  JavaScript: [
    {
      id: "js-closures",
      title: "Closures",
      concept: "A closure is a function that remembers variables from the scope where it was created.",
      explanation: "When an inner function is returned or passed elsewhere, it keeps access to the outer function's variables even after the outer function has finished. Closures enable data privacy, factories and callbacks.",
      keyPoints: [
        "Functions capture their lexical scope.",
        "Closures allow private state without classes.",
        "let in loops creates a new binding per iteration.",
        "Closures can keep memory alive longer than expected."
      ],
      example: `function createCounter() {
  let count = 0;
  return () => ++count;
}

const next = createCounter();
next(); // 1
next(); // 2`,
      tips: [
        "Use the counter example — it is short and clearly shows private state.",
        "Mention the classic var-in-loop bug and how let fixes it."
      ],
      mistakes: [
        "Thinking closures copy values (they keep references to variables).",
        "Using var inside loops with asynchronous callbacks."
      ]
    },
    {
      id: "js-async",
      title: "Promises & async/await",
      concept: "Promises represent values that will be available in the future; async/await is cleaner syntax on top of them.",
      explanation: "A promise is pending, fulfilled or rejected. async functions always return a promise, and await pauses the function until the awaited promise settles. Use try/catch for errors and Promise.all for parallel work.",
      keyPoints: [
        "await only pauses the current async function, not the whole program.",
        "Promise.all runs independent tasks concurrently.",
        "Unhandled rejections should always be caught.",
        "The event loop runs microtasks (promises) before the next macrotask."
      ],
      example: `async function loadDashboard() {
  try {
    const [profile, stats] = await Promise.all([
      fetchProfile(),
      fetchStats()
    ]);
    return { profile, stats };
  } catch (error) {
    console.error("Dashboard failed", error);
  }
}`,
      tips: [
        "Explain why awaiting in a loop can be slower than Promise.all.",
        "Be ready to describe the event loop at a high level."
      ],
      mistakes: [
        "Forgetting to await a promise and using the pending value.",
        "Mixing .then chains and await without reason."
      ]
    }
  ],

  React: [
    {
      id: "react-components",
      title: "Components & Props",
      concept: "Components are reusable functions that return UI; props are their read-only inputs.",
      explanation: "A parent passes data to a child through props. The child must not modify its props. Composition — nesting components and passing children — is preferred over inheritance in React.",
      keyPoints: [
        "Props flow one way: parent to child.",
        "Props are read-only inside the child.",
        "Use keys when rendering lists so React can track items.",
        "Split large components by responsibility."
      ],
      example: `function SkillBadge({ name, level }) {
  return <span className={\`badge \${level}\`}>{name}</span>;
}

<SkillBadge name="Java" level="strong" />`,
      tips: [
        "Explain why array index is a poor key for dynamic lists.",
        "Mention lifting state up when siblings need shared data."
      ],
      mistakes: [
        "Mutating props or state directly.",
        "Using non-unique keys in lists."
      ]
    },
    {
      id: "react-hooks",
      title: "State & Hooks",
      concept: "Hooks let function components hold state and run side effects.",
      explanation: "useState stores local state and triggers re-renders. useEffect runs side effects after rendering, controlled by its dependency array. useRef keeps a mutable value between renders without re-rendering. useMemo and useCallback avoid unnecessary recalculation.",
      keyPoints: [
        "State updates are asynchronous and may be batched.",
        "An empty dependency array runs the effect once after mount.",
        "Return a cleanup function from useEffect for subscriptions and timers.",
        "Only call hooks at the top level of components."
      ],
      example: `const [seconds, setSeconds] = useState(0);

useEffect(() => {
  const id = setInterval(() => setSeconds((s) => s + 1), 1000);
  return () => clearInterval(id);
}, []);`,
      tips: [
        "Show the functional update form setX(prev => ...) and explain why.",
        "Explain what happens when a dependency is missing from the array."
      ],
      mistakes: [
        "Calling hooks inside conditions or loops.",
        "Forgetting cleanup, causing memory leaks."
      ]
    }
  ],

  "Spring Boot": [
    {
      id: "spring-di",
      title: "Dependency Injection",
      concept: "Spring's IoC container creates objects (beans) and supplies their dependencies.",
      explanation: "Instead of a class creating its own dependencies, Spring injects them. Constructor injection is recommended because it makes dependencies explicit, supports final fields and simplifies testing. Beans are singletons by default.",
      keyPoints: [
        "Inversion of Control: the container manages object creation.",
        "Prefer constructor injection over field injection.",
        "@Component, @Service and @Repository register beans.",
        "Default bean scope is singleton."
      ],
      example: `@Service
public class InterviewService {

  private final QuestionRepository questions;

  public InterviewService(QuestionRepository questions) {
    this.questions = questions;
  }
}`,
      tips: [
        "Mention testability: you can pass a mock repository into the constructor.",
        "Know the difference between @Component and @Bean."
      ],
      mistakes: [
        "Using field injection everywhere.",
        "Creating beans with new, bypassing the container."
      ]
    },
    {
      id: "spring-rest",
      title: "REST Controllers",
      concept: "@RestController classes expose HTTP endpoints that return data (usually JSON).",
      explanation: "@RestController combines @Controller and @ResponseBody. Map endpoints with @GetMapping, @PostMapping and so on. Validate input with @Valid and handle errors centrally with @ControllerAdvice.",
      keyPoints: [
        "Use DTOs instead of exposing entities directly.",
        "Return ResponseEntity to control status codes.",
        "@PathVariable and @RequestParam read URL data.",
        "@ControllerAdvice centralises exception handling."
      ],
      example: `@RestController
@RequestMapping("/api/questions")
public class QuestionController {

  @GetMapping("/{id}")
  public ResponseEntity<QuestionDto> get(@PathVariable Long id) {
    return ResponseEntity.ok(service.find(id));
  }
}`,
      tips: [
        "Explain how you return 404 or 400 with a consistent error body.",
        "Mention validation annotations such as @NotBlank."
      ],
      mistakes: [
        "Returning entities with lazy relationships directly as JSON.",
        "Putting business logic inside controllers."
      ]
    }
  ],

  DBMS: [
    {
      id: "dbms-normalization",
      title: "Normalization",
      concept: "Normalization organises tables to reduce redundancy and avoid update anomalies.",
      explanation: "1NF requires atomic values. 2NF removes partial dependencies on a composite key. 3NF removes transitive dependencies. Normalised designs avoid insert, update and delete anomalies, but may need more joins.",
      keyPoints: [
        "1NF: atomic columns, no repeating groups.",
        "2NF: no partial dependency on part of a composite key.",
        "3NF: no transitive dependency between non-key columns.",
        "Denormalisation is sometimes used for read performance."
      ],
      example: `-- Before: orders(order_id, customer_name, customer_city, product)
-- After:
customers(customer_id, name, city)
orders(order_id, customer_id, product)`,
      tips: [
        "Use a small example table and normalise it step by step.",
        "Mention the trade-off between normalisation and query performance."
      ],
      mistakes: [
        "Mixing up 2NF and 3NF definitions.",
        "Claiming more normalisation is always better."
      ]
    },
    {
      id: "dbms-transactions",
      title: "Transactions & ACID",
      concept: "A transaction groups operations so they succeed or fail together.",
      explanation: "ACID stands for Atomicity, Consistency, Isolation and Durability. Isolation levels (Read Uncommitted, Read Committed, Repeatable Read, Serializable) trade consistency for concurrency.",
      keyPoints: [
        "Atomicity: all or nothing.",
        "Consistency: constraints are preserved.",
        "Isolation: concurrent transactions do not interfere.",
        "Durability: committed data survives failures."
      ],
      example: `START TRANSACTION;
UPDATE accounts SET balance = balance - 500 WHERE id = 1;
UPDATE accounts SET balance = balance + 500 WHERE id = 2;
COMMIT;`,
      tips: [
        "Use the bank-transfer example to explain atomicity.",
        "Name at least one anomaly such as dirty reads."
      ],
      mistakes: [
        "Confusing consistency in ACID with eventual consistency.",
        "Not knowing the default isolation level of the database you use."
      ]
    }
  ],

  "Operating Systems": [
    {
      id: "os-process-thread",
      title: "Processes vs Threads",
      concept: "A process is an independent program in execution; a thread is a unit of execution inside a process.",
      explanation: "Processes have separate memory spaces, which makes them isolated but expensive to create and switch. Threads in the same process share heap and resources but have their own stack and registers, making them lighter but requiring synchronisation.",
      keyPoints: [
        "Processes are isolated; threads share memory.",
        "Context switching between threads is cheaper.",
        "Inter-process communication uses pipes, sockets or shared memory.",
        "Threads need synchronisation to avoid race conditions."
      ],
      example: `Process A          Process B
├─ Heap            ├─ Heap
├─ Thread 1 stack  └─ Thread 1 stack
└─ Thread 2 stack`,
      tips: [
        "Relate the answer to a browser using processes per tab.",
        "Mention what is shared and what is private for threads."
      ],
      mistakes: [
        "Saying threads have separate heaps.",
        "Ignoring the cost of context switching."
      ]
    },
    {
      id: "os-deadlock",
      title: "Deadlocks",
      concept: "A deadlock happens when processes wait forever for resources held by each other.",
      explanation: "Four conditions must hold together: mutual exclusion, hold and wait, no preemption and circular wait. Breaking any one prevents deadlock. Avoidance algorithms such as the Banker's algorithm check safe states before granting resources.",
      keyPoints: [
        "Coffman conditions: mutual exclusion, hold & wait, no preemption, circular wait.",
        "Prevention breaks one of the conditions.",
        "Avoidance uses safe-state checks (Banker's algorithm).",
        "Detection and recovery kill or roll back processes."
      ],
      example: `Thread 1: lock(A) → waits for lock(B)
Thread 2: lock(B) → waits for lock(A)
→ circular wait → deadlock`,
      tips: [
        "Explain lock ordering as a practical prevention technique.",
        "Differentiate deadlock from starvation."
      ],
      mistakes: [
        "Listing only three of the four conditions.",
        "Confusing prevention with avoidance."
      ]
    }
  ],

  "Computer Networks": [
    {
      id: "cn-tcp-udp",
      title: "TCP vs UDP",
      concept: "TCP and UDP are transport-layer protocols with different reliability guarantees.",
      explanation: "TCP is connection-oriented: it uses a three-way handshake and guarantees ordered, reliable delivery with retransmission and flow control. UDP is connectionless with lower overhead, so it suits real-time applications that tolerate loss.",
      keyPoints: [
        "TCP: reliable, ordered, connection-oriented.",
        "UDP: fast, connectionless, no delivery guarantee.",
        "TCP uses SYN, SYN-ACK, ACK to connect.",
        "Typical UDP uses: DNS, video calls, gaming."
      ],
      example: `Client → SYN      → Server
Client ← SYN-ACK  ← Server
Client → ACK      → Server   (connection established)`,
      tips: [
        "Give one real application for each protocol.",
        "Mention that HTTP/3 runs over QUIC, which uses UDP."
      ],
      mistakes: [
        "Saying UDP is always unreliable for every use case.",
        "Forgetting flow and congestion control in TCP."
      ]
    },
    {
      id: "cn-dns",
      title: "DNS Resolution",
      concept: "DNS translates human-readable domain names into IP addresses.",
      explanation: "A browser checks its cache, then asks a recursive resolver. The resolver queries root servers, then TLD servers (.com), then the domain's authoritative server, and caches the result according to its TTL.",
      keyPoints: [
        "Resolution path: cache → resolver → root → TLD → authoritative.",
        "A records map to IPv4, AAAA to IPv6, CNAME to another name.",
        "TTL controls how long results are cached.",
        "DNS mostly uses UDP port 53."
      ],
      example: `careerai.app → resolver → root → .app TLD → authoritative
                         ← 203.0.113.10`,
      tips: [
        "Use 'what happens when you type a URL' to show the full picture.",
        "Mention caching at multiple levels."
      ],
      mistakes: [
        "Skipping the recursive resolver step.",
        "Confusing DNS with DHCP."
      ]
    }
  ],

  Git: [
    {
      id: "git-branching",
      title: "Branching & Merging",
      concept: "Branches let you work on changes in isolation and combine them later.",
      explanation: "A branch is a movable pointer to a commit. Merge combines histories and may create a merge commit. Rebase replays commits onto another base for a linear history but rewrites commit IDs.",
      keyPoints: [
        "git switch -c feature creates and switches to a branch.",
        "Merge preserves history; rebase creates a linear history.",
        "Never rebase commits that others have already pulled.",
        "Resolve conflicts, then commit or continue the rebase."
      ],
      example: `git switch -c feature/login
git commit -m "Add login form"
git switch main
git merge feature/login`,
      tips: [
        "Describe your team's workflow (feature branches + pull requests).",
        "Explain how you resolve a merge conflict."
      ],
      mistakes: [
        "Force-pushing rebased commits to a shared branch.",
        "Committing directly to main in team projects."
      ]
    },
    {
      id: "git-undo",
      title: "Undoing Changes",
      concept: "Git offers several ways to undo work depending on whether it is staged, committed or pushed.",
      explanation: "git restore discards working-tree changes. git reset moves the branch pointer (soft keeps changes staged, mixed unstages them, hard discards them). git revert creates a new commit that undoes a previous one, which is safe for shared history.",
      keyPoints: [
        "git restore <file> discards local changes.",
        "git reset --soft HEAD~1 undoes the last commit but keeps changes staged.",
        "git revert is safe for commits that are already pushed.",
        "git stash temporarily shelves uncommitted work."
      ],
      example: `git reset --soft HEAD~1   # undo last commit, keep changes
git revert a1b2c3d        # safely undo a pushed commit
git stash && git stash pop`,
      tips: [
        "Explain why revert is preferred over reset on shared branches.",
        "Mention git reflog as a safety net."
      ],
      mistakes: [
        "Using reset --hard without realising changes are lost.",
        "Rewriting history that teammates already have."
      ]
    }
  ],

  "REST APIs": [
    {
      id: "rest-methods",
      title: "HTTP Methods & Status Codes",
      concept: "REST APIs use HTTP methods to express actions and status codes to express outcomes.",
      explanation: "GET reads, POST creates, PUT replaces, PATCH partially updates and DELETE removes. Status codes communicate results: 2xx success, 4xx client errors and 5xx server errors.",
      keyPoints: [
        "GET, PUT and DELETE should be idempotent.",
        "201 Created for successful creation, often with a Location header.",
        "400 for invalid input, 401 unauthenticated, 403 forbidden, 404 not found.",
        "500 for unexpected server errors."
      ],
      example: `POST   /api/resumes        → 201 Created
GET    /api/resumes/42     → 200 OK
PATCH  /api/resumes/42     → 200 OK
DELETE /api/resumes/42     → 204 No Content`,
      tips: [
        "Explain idempotency with a retry scenario.",
        "Know the difference between 401 and 403."
      ],
      mistakes: [
        "Returning 200 with an error message in the body.",
        "Using GET for operations that change data."
      ]
    },
    {
      id: "rest-design",
      title: "Resource Design",
      concept: "Good REST APIs model nouns (resources) with consistent, predictable URLs.",
      explanation: "Use plural nouns, nest only for clear ownership, support filtering and pagination with query parameters, and version the API. Keep requests stateless: each request carries everything needed, such as an auth token.",
      keyPoints: [
        "Use nouns, not verbs: /orders not /getOrders.",
        "Paginate large collections: ?page=2&size=20.",
        "Version with /api/v1 or headers.",
        "Statelessness makes horizontal scaling easier."
      ],
      example: `GET /api/v1/users/7/applications?status=interview&page=1&size=10`,
      tips: [
        "Discuss how you would version a breaking change.",
        "Mention consistent error response formats."
      ],
      mistakes: [
        "Deep nesting such as /users/1/orders/2/items/3/notes.",
        "Storing session state on the server."
      ]
    }
  ],

  "Spring Security": [
    {
      id: "spring-security-filter-chain",
      title: "Authentication & Filter Chain",
      concept: "Spring Security protects an application with a chain of servlet filters that run before requests reach your controllers.",
      explanation: "Every request passes through the SecurityFilterChain. Authentication filters read credentials (a form login, HTTP Basic header or JWT), an AuthenticationManager verifies them through a UserDetailsService and PasswordEncoder, and the authenticated user is stored in the SecurityContext. Authorization rules then decide whether the request may continue.",
      keyPoints: [
        "SecurityFilterChain bean configures which URLs are public and which need authentication.",
        "UserDetailsService loads the user; PasswordEncoder (BCrypt) checks the password hash.",
        "SecurityContextHolder stores the current Authentication for the request.",
        "401 means not authenticated; 403 means authenticated but not allowed."
      ],
      example: `@Bean
SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .build();
}`,
      tips: [
        "Walk through the request flow from filter to controller.",
        "Explain why passwords are hashed with BCrypt and never decrypted."
      ],
      mistakes: [
        "Storing plain-text passwords.",
        "Disabling CSRF without understanding why (it is safe for stateless token APIs, not for session-based forms)."
      ]
    },
    {
      id: "spring-security-jwt",
      title: "JWT & Authorization",
      concept: "A JSON Web Token lets a stateless API identify the user on every request without a server-side session.",
      explanation: "After login the server signs a JWT containing the user ID, roles and an expiry. The client sends it in the Authorization: Bearer header. A custom filter validates the signature and expiry, then sets the Authentication in the SecurityContext. Role checks use hasRole() in the filter chain or @PreAuthorize on methods.",
      keyPoints: [
        "A JWT has a header, payload (claims) and signature.",
        "The payload is encoded, not encrypted — never store secrets in it.",
        "Short-lived access tokens plus refresh tokens limit damage from leaks.",
        "@PreAuthorize(\"hasRole('ADMIN')\") enables method-level authorization."
      ],
      example: `Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/api/users/{id}")
public void delete(@PathVariable Long id) { ... }`,
      tips: [
        "Explain how you would revoke or expire tokens.",
        "Compare session-based and token-based authentication."
      ],
      mistakes: [
        "Putting the signing secret in frontend code.",
        "Issuing tokens that never expire."
      ]
    }
  ],

  Docker: [
    {
      id: "docker-images-containers",
      title: "Images & Containers",
      concept: "Docker packages an application and its runtime into an image, and runs that image as an isolated container.",
      explanation: "An image is a read-only template built from a Dockerfile in layers. A container is a running instance of an image with its own filesystem, network and processes, but it shares the host kernel, which makes it much lighter than a virtual machine.",
      keyPoints: [
        "Image = blueprint; container = running instance.",
        "Each Dockerfile instruction creates a cached layer.",
        "Containers share the host kernel, unlike VMs.",
        "Use volumes for data that must survive container restarts."
      ],
      example: `FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/careerai.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]`,
      tips: [
        "Order Dockerfile steps so rarely changing layers come first.",
        "Mention multi-stage builds to keep images small."
      ],
      mistakes: [
        "Saying containers are lightweight virtual machines.",
        "Baking secrets into the image."
      ]
    },
    {
      id: "docker-compose",
      title: "Docker Compose & Networking",
      concept: "Docker Compose defines and runs multi-container applications from one YAML file.",
      explanation: "A typical full-stack app runs a backend, a database and sometimes a frontend. Compose starts them on a shared network where services reach each other by service name, and it wires environment variables, ports and volumes in one place.",
      keyPoints: [
        "Services on the same Compose network resolve each other by name (e.g. db:3306).",
        "ports maps host:container ports.",
        "depends_on controls start order, not readiness.",
        "Environment variables configure each environment without rebuilding."
      ],
      example: `services:
  api:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/careerai
    depends_on: [db]
  db:
    image: mysql:8
    volumes: ["db-data:/var/lib/mysql"]
volumes:
  db-data:`,
      tips: [
        "Explain how the API container finds the database container.",
        "Mention health checks for readiness."
      ],
      mistakes: [
        "Using localhost to reach another container.",
        "Losing database data by not using a volume."
      ]
    }
  ]
};

/* =====================================================
   QUIZ (MCQ)
   { question, options[], answer (option text), explanation, tag }
===================================================== */

export const technicalMcqs = {
  Java: [
    {
      question: "Which collection does not allow duplicate elements?",
      options: ["ArrayList", "LinkedList", "HashMap", "HashSet"],
      answer: "HashSet",
      explanation: "HashSet implements Set, which stores only unique elements. ArrayList and LinkedList allow duplicates. HashMap is a Map: its keys are unique but values may repeat, and it is not a Collection of elements.",
      tag: "Collections"
    },
    {
      question: "Which keyword prevents a method from being overridden?",
      options: ["final", "abstract", "volatile", "transient"],
      answer: "final",
      explanation: "A final method cannot be overridden by subclasses. abstract requires overriding, while volatile and transient apply to fields.",
      tag: "OOP"
    },
    {
      question: "What is the default value of an int instance variable?",
      options: ["0", "null", "undefined", "Random value"],
      answer: "0",
      explanation: "Instance fields get default values: 0 for numeric primitives, false for boolean and null for references. Local variables have no default and must be initialised.",
      tag: "Language Basics"
    },
    {
      question: "Which of these is a checked exception?",
      options: ["NullPointerException", "ArithmeticException", "IOException", "ArrayIndexOutOfBoundsException"],
      answer: "IOException",
      explanation: "IOException extends Exception (not RuntimeException), so the compiler requires it to be caught or declared.",
      tag: "Exception Handling"
    },
    {
      question: "Which block executes whether or not an exception is thrown?",
      options: ["catch", "finally", "throw", "throws"],
      answer: "finally",
      explanation: "finally runs after try/catch in almost all cases, which makes it suitable for cleanup. The rare exception is when the JVM exits (for example, System.exit).",
      tag: "Exception Handling"
    },
    {
      question: "Which interface represents a task that a Thread can run and that returns no result?",
      options: ["Runnable", "Serializable", "Comparable", "Cloneable"],
      answer: "Runnable",
      explanation: "Runnable has a single run() method with no return value. Callable is used when the task returns a result.",
      tag: "Multithreading"
    },
    {
      question: "What does the volatile keyword guarantee?",
      options: [
        "Visibility of changes across threads",
        "Atomicity of compound operations such as count++",
        "That the class cannot be subclassed",
        "That the variable is a constant"
      ],
      answer: "Visibility of changes across threads",
      explanation: "volatile ensures every thread reads the latest value from main memory. It does not make compound operations atomic — use AtomicInteger or synchronisation for that.",
      tag: "Multithreading"
    },
    {
      question: "Which Stream operation is terminal?",
      options: ["map", "filter", "collect", "sorted"],
      answer: "collect",
      explanation: "collect produces a result and ends the pipeline. map, filter and sorted are lazy intermediate operations.",
      tag: "Streams"
    },
    {
      question: "Where are Java objects allocated?",
      options: ["Heap", "Stack", "PC register", "Native method stack"],
      answer: "Heap",
      explanation: "Objects are allocated on the heap and reclaimed by the garbage collector. The stack holds method frames and local variables (including references to heap objects).",
      tag: "JVM Basics"
    },
    {
      question: "Which statement about String in Java is correct?",
      options: [
        "String objects are immutable",
        "String is a primitive type",
        "Strings can be modified in place with setCharAt()",
        "String concatenation always modifies the original object"
      ],
      answer: "String objects are immutable",
      explanation: "Once created, a String cannot change. Operations such as concat return a new String. Use StringBuilder for repeated modifications.",
      tag: "Language Basics"
    }
  ],

  Python: [
    {
      question: "Which built-in type is immutable?",
      options: ["list", "dict", "set", "tuple"],
      answer: "tuple",
      explanation: "Tuples cannot be modified after creation, which also makes them hashable when their contents are hashable.",
      tag: "Data Types"
    },
    {
      question: "What does len({1, 2, 2, 3}) return?",
      options: ["4", "3", "2", "It raises an error"],
      answer: "3",
      explanation: "{1, 2, 2, 3} is a set literal, so the duplicate 2 is removed and the set has three elements.",
      tag: "Data Types"
    },
    {
      question: "What does a decorator do?",
      options: [
        "Wraps a function to extend its behaviour without changing its code",
        "Declares a variable as global",
        "Creates a new class automatically",
        "Imports a module lazily"
      ],
      answer: "Wraps a function to extend its behaviour without changing its code",
      explanation: "A decorator takes a function and returns a new function (usually a wrapper) that adds behaviour such as logging or caching.",
      tag: "Functions"
    },
    {
      question: "Which keyword turns a function into a generator?",
      options: ["return", "yield", "async", "lambda"],
      answer: "yield",
      explanation: "A function containing yield returns a generator that produces values lazily, one at a time.",
      tag: "Functions"
    }
  ],

  JavaScript: [
    {
      question: "What does typeof null return?",
      options: ["\"null\"", "\"object\"", "\"undefined\"", "\"number\""],
      answer: "\"object\"",
      explanation: "This is a long-standing quirk in JavaScript: typeof null returns \"object\". Use value === null to check for null.",
      tag: "Language Basics"
    },
    {
      question: "Which declaration is block-scoped and cannot be reassigned?",
      options: ["var", "let", "const", "function"],
      answer: "const",
      explanation: "const is block-scoped and cannot be reassigned, although objects it references can still be mutated. let is block-scoped but reassignable; var is function-scoped.",
      tag: "Language Basics"
    },
    {
      question: "What is a closure?",
      options: [
        "A function that keeps access to variables from its outer scope",
        "A way to close a browser window",
        "A method that ends a promise chain",
        "A block that catches errors"
      ],
      answer: "A function that keeps access to variables from its outer scope",
      explanation: "Closures capture their lexical environment, so an inner function can use outer variables even after the outer function returns.",
      tag: "Closures"
    },
    {
      question: "What does await do inside an async function?",
      options: [
        "Pauses that function until the promise settles",
        "Blocks the entire browser until the promise settles",
        "Converts a promise into a callback",
        "Cancels the promise"
      ],
      answer: "Pauses that function until the promise settles",
      explanation: "await suspends only the current async function; other code and the event loop keep running.",
      tag: "Async"
    }
  ],

  React: [
    {
      question: "Which hook stores local component state?",
      options: ["useState", "useEffect", "useRef", "useMemo"],
      answer: "useState",
      explanation: "useState returns a state value and a setter; calling the setter triggers a re-render.",
      tag: "Hooks"
    },
    {
      question: "Why do list items need a key prop?",
      options: [
        "So React can identify which items changed, were added or removed",
        "To apply CSS styles",
        "To make items clickable",
        "To sort the list automatically"
      ],
      answer: "So React can identify which items changed, were added or removed",
      explanation: "Stable keys let React's reconciliation match items between renders efficiently and preserve their state.",
      tag: "Components"
    },
    {
      question: "Which statement about props is correct?",
      options: [
        "Props are read-only inputs passed from a parent",
        "Props can be changed directly by the child",
        "Props are only available in class components",
        "Props replace state entirely"
      ],
      answer: "Props are read-only inputs passed from a parent",
      explanation: "A child must treat props as immutable. To change data, the parent updates its state and passes new props.",
      tag: "Components"
    },
    {
      question: "When does useEffect(fn, []) run?",
      options: [
        "Once, after the first render",
        "Before every render",
        "After every render",
        "Only when the component unmounts"
      ],
      answer: "Once, after the first render",
      explanation: "An empty dependency array means the effect has no dependencies that change, so it runs after mount (its cleanup runs on unmount).",
      tag: "Hooks"
    }
  ],

  "Spring Boot": [
    {
      question: "Which annotation is typically placed on the main application class?",
      options: ["@SpringBootApplication", "@RestController", "@Entity", "@Service"],
      answer: "@SpringBootApplication",
      explanation: "@SpringBootApplication combines @Configuration, @EnableAutoConfiguration and @ComponentScan.",
      tag: "Basics"
    },
    {
      question: "Which type of dependency injection is recommended?",
      options: ["Constructor injection", "Field injection", "Static injection", "Reflection injection"],
      answer: "Constructor injection",
      explanation: "Constructor injection makes dependencies explicit, allows final fields and makes classes easy to test.",
      tag: "Dependency Injection"
    },
    {
      question: "@RestController is a combination of which annotations?",
      options: [
        "@Controller and @ResponseBody",
        "@Service and @Repository",
        "@Component and @Bean",
        "@Configuration and @Bean"
      ],
      answer: "@Controller and @ResponseBody",
      explanation: "@ResponseBody makes every handler method write its return value directly to the HTTP response body.",
      tag: "REST Controllers"
    },
    {
      question: "Which embedded server does Spring Boot use by default for web applications?",
      options: ["Tomcat", "Jetty", "Undertow", "Nginx"],
      answer: "Tomcat",
      explanation: "spring-boot-starter-web includes embedded Tomcat by default. Jetty and Undertow are optional alternatives.",
      tag: "Basics"
    }
  ],

  DBMS: [
    {
      question: "Which normal form removes partial dependencies?",
      options: ["1NF", "2NF", "3NF", "BCNF"],
      answer: "2NF",
      explanation: "2NF requires that non-key attributes depend on the whole composite key, not just part of it.",
      tag: "Normalization"
    },
    {
      question: "In ACID, what does the 'I' stand for?",
      options: ["Isolation", "Integrity", "Indexing", "Immutability"],
      answer: "Isolation",
      explanation: "Isolation ensures concurrent transactions do not interfere with each other's intermediate states.",
      tag: "Transactions"
    },
    {
      question: "Which statement about a primary key is correct?",
      options: [
        "It uniquely identifies each row and cannot be NULL",
        "It can contain duplicate values",
        "A table can have several primary keys",
        "It must always be a string"
      ],
      answer: "It uniquely identifies each row and cannot be NULL",
      explanation: "A table has one primary key (which may span several columns); its values are unique and non-null.",
      tag: "Keys"
    },
    {
      question: "Which command removes all rows but keeps the table structure?",
      options: ["DROP", "TRUNCATE", "ALTER", "RENAME"],
      answer: "TRUNCATE",
      explanation: "TRUNCATE quickly removes every row while keeping the table definition. DROP removes the table itself.",
      tag: "SQL Commands"
    }
  ],

  "Operating Systems": [
    {
      question: "Which is NOT one of the four necessary conditions for deadlock?",
      options: ["Mutual exclusion", "Hold and wait", "Circular wait", "Preemptive scheduling"],
      answer: "Preemptive scheduling",
      explanation: "The four Coffman conditions are mutual exclusion, hold and wait, no preemption and circular wait.",
      tag: "Deadlocks"
    },
    {
      question: "What do threads of the same process share?",
      options: ["Heap memory", "Stack", "Program counter", "Registers"],
      answer: "Heap memory",
      explanation: "Threads share the process's heap, code and open files, but each has its own stack, registers and program counter.",
      tag: "Processes & Threads"
    },
    {
      question: "Paging mainly helps to eliminate which problem?",
      options: ["External fragmentation", "Deadlock", "Starvation", "Race conditions"],
      answer: "External fragmentation",
      explanation: "Paging divides memory into fixed-size frames, so free memory does not need to be contiguous.",
      tag: "Memory Management"
    },
    {
      question: "Which scheduling algorithm can cause starvation of long processes?",
      options: ["Shortest Job First", "First Come First Served", "Round Robin", "None of these"],
      answer: "Shortest Job First",
      explanation: "SJF keeps picking short jobs, so long jobs may wait indefinitely if short jobs keep arriving.",
      tag: "Scheduling"
    }
  ],

  "Computer Networks": [
    {
      question: "Which transport protocol is connection-oriented?",
      options: ["TCP", "UDP", "ICMP", "ARP"],
      answer: "TCP",
      explanation: "TCP establishes a connection with a three-way handshake before sending data.",
      tag: "Transport Layer"
    },
    {
      question: "What does DNS do?",
      options: [
        "Translates domain names into IP addresses",
        "Assigns IP addresses to devices",
        "Encrypts web traffic",
        "Routes packets between networks"
      ],
      answer: "Translates domain names into IP addresses",
      explanation: "DNS resolves names such as careerai.app to IP addresses. DHCP assigns addresses to devices.",
      tag: "Application Layer"
    },
    {
      question: "What is the default port for HTTPS?",
      options: ["443", "80", "21", "25"],
      answer: "443",
      explanation: "HTTPS uses port 443 by default; plain HTTP uses port 80.",
      tag: "Application Layer"
    },
    {
      question: "At which OSI layer does routing happen?",
      options: ["Network layer", "Transport layer", "Data link layer", "Session layer"],
      answer: "Network layer",
      explanation: "Routers operate at the network layer (layer 3) using IP addresses to forward packets.",
      tag: "OSI Model"
    }
  ],

  Git: [
    {
      question: "Which command creates a new branch and switches to it?",
      options: ["git checkout -b feature", "git branch -d feature", "git merge feature", "git init feature"],
      answer: "git checkout -b feature",
      explanation: "git checkout -b (or git switch -c) creates the branch and moves HEAD to it in one step.",
      tag: "Branching"
    },
    {
      question: "What does git stash do?",
      options: [
        "Temporarily saves uncommitted changes",
        "Deletes the last commit",
        "Pushes changes to the remote",
        "Creates a new repository"
      ],
      answer: "Temporarily saves uncommitted changes",
      explanation: "git stash shelves your working changes so you can switch context, then git stash pop restores them.",
      tag: "Undoing Changes"
    },
    {
      question: "Which command undoes the last commit but keeps its changes staged?",
      options: ["git reset --soft HEAD~1", "git reset --hard HEAD~1", "git revert HEAD", "git clean -fd"],
      answer: "git reset --soft HEAD~1",
      explanation: "--soft moves the branch pointer back but keeps the changes in the staging area. --hard discards them.",
      tag: "Undoing Changes"
    },
    {
      question: "git pull is equivalent to which two operations?",
      options: ["fetch + merge", "push + merge", "clone + checkout", "commit + push"],
      answer: "fetch + merge",
      explanation: "git pull fetches remote changes and merges them into the current branch (or rebases with --rebase).",
      tag: "Remote Workflow"
    }
  ],

  "REST APIs": [
    {
      question: "Which HTTP method is typically used to create a new resource?",
      options: ["POST", "GET", "DELETE", "HEAD"],
      answer: "POST",
      explanation: "POST submits data to create a resource. A successful creation usually returns 201 Created.",
      tag: "HTTP Methods"
    },
    {
      question: "Which status code means a resource was created successfully?",
      options: ["201", "200", "204", "302"],
      answer: "201",
      explanation: "201 Created indicates a new resource was created, often with a Location header pointing to it.",
      tag: "Status Codes"
    },
    {
      question: "Which status code should be returned when the user is authenticated but not allowed?",
      options: ["403", "401", "404", "500"],
      answer: "403",
      explanation: "403 Forbidden means the server knows who you are but you lack permission. 401 means you are not authenticated.",
      tag: "Status Codes"
    },
    {
      question: "What does 'stateless' mean in REST?",
      options: [
        "Each request contains all the information needed to process it",
        "The server never stores any data",
        "Responses are never cached",
        "Clients cannot store tokens"
      ],
      answer: "Each request contains all the information needed to process it",
      explanation: "The server does not rely on stored session context between requests; data can still be stored in databases.",
      tag: "REST Principles"
    }
  ],

  "Spring Security": [
    {
      question: "Which component loads a user's details during authentication in Spring Security?",
      options: ["UserDetailsService", "DispatcherServlet", "JpaRepository", "RestTemplate"],
      answer: "UserDetailsService",
      explanation: "UserDetailsService.loadUserByUsername returns the user, which the AuthenticationManager checks against the submitted credentials.",
      tag: "Authentication"
    },
    {
      question: "Which HTTP status code means the user is authenticated but not allowed to access the resource?",
      options: ["403", "401", "404", "400"],
      answer: "403",
      explanation: "401 means not authenticated; 403 Forbidden means the identity is known but lacks permission.",
      tag: "Authorization"
    },
    {
      question: "Which password encoder is recommended for storing passwords?",
      options: ["BCryptPasswordEncoder", "NoOpPasswordEncoder", "Base64 encoding", "MD5 hashing"],
      answer: "BCryptPasswordEncoder",
      explanation: "BCrypt is a slow, salted, adaptive hash. Base64 is reversible and MD5 is too fast to resist brute force.",
      tag: "Password Storage"
    },
    {
      question: "Where does a client usually send a JWT on each request?",
      options: ["Authorization: Bearer header", "Request URL path", "Response body", "Server session"],
      answer: "Authorization: Bearer header",
      explanation: "Stateless APIs read the token from the Authorization header and validate it in a security filter.",
      tag: "JWT"
    }
  ],

  Docker: [
    {
      question: "What is the relationship between a Docker image and a container?",
      options: [
        "A container is a running instance of an image",
        "An image is a running instance of a container",
        "They are the same thing",
        "A container is a compressed image file"
      ],
      answer: "A container is a running instance of an image",
      explanation: "Images are read-only templates; containers add a writable layer and run processes.",
      tag: "Images & Containers"
    },
    {
      question: "Which Dockerfile instruction defines the command that runs when the container starts?",
      options: ["ENTRYPOINT", "COPY", "WORKDIR", "EXPOSE"],
      answer: "ENTRYPOINT",
      explanation: "ENTRYPOINT (or CMD) sets the startup command. EXPOSE only documents a port.",
      tag: "Dockerfile"
    },
    {
      question: "How should data be kept when a database container is recreated?",
      options: ["Use a volume", "Store it in the image", "Use EXPOSE", "Restart the host"],
      answer: "Use a volume",
      explanation: "Volumes live outside the container's writable layer, so data survives container removal.",
      tag: "Volumes"
    },
    {
      question: "In Docker Compose, how does the api service reach a service named db?",
      options: ["Using the hostname db", "Using localhost", "Using the host's public IP", "It cannot reach it"],
      answer: "Using the hostname db",
      explanation: "Compose puts services on a shared network where each service name resolves via DNS.",
      tag: "Compose"
    }
  ]
};

/* =====================================================
   DESCRIPTIVE
   keyPoints[].keywords are matched (case-insensitive) against the answer
   to produce demo feedback.
   TODO backend: descriptive answers will be evaluated by CareerAI's
   Spring Boot service (Gemini) and return the same feedback shape.
===================================================== */

export const technicalDescriptive = {
  Java: [
    {
      question: "Explain the difference between an interface and an abstract class.",
      keyPoints: [
        { label: "Abstract classes can hold state and constructors", keywords: ["state", "field", "constructor", "instance variable"] },
        { label: "A class can implement many interfaces but extend only one class", keywords: ["multiple", "only one", "single inheritance", "implement many", "extends one"] },
        { label: "Interfaces can have default and static methods (Java 8+)", keywords: ["default method", "java 8", "static method", "default"] },
        { label: "When to choose each (shared base vs capability/contract)", keywords: ["contract", "capability", "is-a", "can-do", "when to use", "choose"] }
      ],
      strongerAnswer: "An abstract class is a partial implementation: it can have fields, constructors and both abstract and concrete methods, and a class can extend only one. An interface defines a contract; since Java 8 it can also contain default and static methods, and a class can implement many interfaces. I use an abstract class when related classes share state or code (an is-a relationship), and an interface when unrelated classes need a common capability, such as Comparable."
    },
    {
      question: "How does HashMap work internally?",
      keyPoints: [
        { label: "Uses hashCode() to choose a bucket in an internal array", keywords: ["hashcode", "hash code", "bucket", "array", "index"] },
        { label: "Handles collisions with a linked list or a tree", keywords: ["collision", "linked list", "tree", "treeify", "red-black"] },
        { label: "Uses equals() to find the exact key inside a bucket", keywords: ["equals"] },
        { label: "Resizes when the load factor threshold is exceeded", keywords: ["load factor", "resize", "rehash", "0.75", "capacity"] }
      ],
      strongerAnswer: "HashMap stores entries in an array of buckets. When you put a key, it computes hashCode(), spreads the bits and maps it to a bucket index. If several keys land in the same bucket, they are stored in a linked list, which becomes a balanced tree after eight entries. Lookups use equals() to find the exact key. When the number of entries exceeds capacity × load factor (0.75 by default), the table doubles and entries are redistributed."
    }
  ],
  Python: [
    {
      question: "Explain the difference between a list and a tuple, and when you would use each.",
      keyPoints: [
        { label: "Lists are mutable; tuples are immutable", keywords: ["mutable", "immutable", "cannot change", "modify"] },
        { label: "Tuples can be dictionary keys because they are hashable", keywords: ["hashable", "dictionary key", "dict key", "key"] },
        { label: "Tuples are slightly faster and use less memory", keywords: ["faster", "memory", "performance"] },
        { label: "Use tuples for fixed records and lists for changing collections", keywords: ["fixed", "record", "coordinates", "when to use", "collection"] }
      ],
      strongerAnswer: "Lists are mutable sequences used for collections that change, such as a list of applicants. Tuples are immutable, slightly lighter and hashable (if their items are), so they can be dictionary keys or set members. I use tuples for fixed records like coordinates or function return values, and lists when I need to append, remove or sort items."
    }
  ],
  JavaScript: [
    {
      question: "Explain closures in JavaScript with an example.",
      keyPoints: [
        { label: "A function remembers its lexical scope", keywords: ["lexical", "scope", "outer", "environment"] },
        { label: "Variables remain accessible after the outer function returns", keywords: ["after", "returned", "returns", "still access"] },
        { label: "Practical use: private state, counters or factories", keywords: ["private", "counter", "factory", "encapsulat"] },
        { label: "Includes a concrete example", keywords: ["function", "=>", "return", "example"] }
      ],
      strongerAnswer: "A closure is a function bundled with references to the variables in the scope where it was created. For example, createCounter declares let count = 0 and returns () => ++count. Each call to the returned function increments the same count, even though createCounter has already finished. Closures are used for private state, factories and callbacks, and let in loops avoids the classic shared-variable bug."
    }
  ],
  React: [
    {
      question: "Explain the difference between state and props in React.",
      keyPoints: [
        { label: "Props are passed from parent and are read-only", keywords: ["parent", "read-only", "read only", "immutable", "passed"] },
        { label: "State is local and managed by the component (useState)", keywords: ["local", "usestate", "internal", "managed"] },
        { label: "Changing state triggers a re-render", keywords: ["re-render", "rerender", "render"] },
        { label: "Lifting state up to share data between components", keywords: ["lift", "shared", "share"] }
      ],
      strongerAnswer: "Props are inputs a parent passes to a child; the child treats them as read-only. State is data owned by a component, created with useState, and updating it re-renders the component. When two siblings need the same data, I lift the state to their closest common parent and pass it down as props, together with callbacks to update it."
    }
  ],
  "Spring Boot": [
    {
      question: "Explain dependency injection in Spring Boot.",
      keyPoints: [
        { label: "Inversion of control: the container creates and wires objects", keywords: ["inversion of control", "ioc", "container", "application context"] },
        { label: "Constructor injection", keywords: ["constructor"] },
        { label: "Beans registered with @Component/@Service and injected", keywords: ["bean", "@component", "@service", "@autowired", "annotation"] },
        { label: "Dependency lifecycle and scope (singleton by default)", keywords: ["lifecycle", "scope", "singleton", "prototype"] },
        { label: "Benefit: loose coupling and easier testing", keywords: ["loose", "coupling", "test", "mock"] }
      ],
      strongerAnswer: "Dependency injection means a class receives its dependencies instead of creating them. Spring's IoC container scans for beans (@Component, @Service, @Repository), creates them — as singletons by default — and injects them where needed. Constructor injection is preferred: dependencies are explicit, fields can be final and tests can pass mocks directly. This keeps classes loosely coupled and lets Spring manage the full bean lifecycle."
    },
    {
      question: "What happens when a Spring Boot application starts?",
      keyPoints: [
        { label: "@SpringBootApplication enables component scanning", keywords: ["@springbootapplication", "component scan", "scan"] },
        { label: "Auto-configuration based on the classpath", keywords: ["auto-configuration", "autoconfiguration", "auto configuration", "classpath", "starter"] },
        { label: "ApplicationContext creates and wires beans", keywords: ["application context", "context", "bean"] },
        { label: "Embedded server (Tomcat) starts", keywords: ["tomcat", "embedded", "server"] }
      ],
      strongerAnswer: "SpringApplication.run creates the ApplicationContext. @SpringBootApplication triggers component scanning from the main package and enables auto-configuration, which inspects the classpath (for example, spring-boot-starter-web) and configures beans such as the DispatcherServlet and a DataSource. Beans are created and injected, application properties are bound, and for web apps the embedded Tomcat server starts listening on port 8080."
    }
  ],
  DBMS: [
    {
      question: "Explain normalization and why it is used.",
      keyPoints: [
        { label: "Reduces data redundancy", keywords: ["redundan", "duplicate"] },
        { label: "Prevents insert, update and delete anomalies", keywords: ["anomal", "inconsisten"] },
        { label: "Describes normal forms (1NF, 2NF, 3NF)", keywords: ["1nf", "2nf", "3nf", "normal form"] },
        { label: "Mentions the trade-off with joins or denormalisation", keywords: ["join", "denormal", "trade-off", "performance"] }
      ],
      strongerAnswer: "Normalization structures tables so each fact is stored once. 1NF requires atomic values, 2NF removes partial dependencies on a composite key and 3NF removes transitive dependencies. This prevents insert, update and delete anomalies and keeps data consistent. The trade-off is more joins, so read-heavy systems sometimes denormalise selected data deliberately."
    }
  ],
  "Operating Systems": [
    {
      question: "What is a deadlock and how can it be prevented?",
      keyPoints: [
        { label: "Defines deadlock as processes waiting on each other forever", keywords: ["wait", "forever", "each other", "blocked"] },
        { label: "Lists the four necessary conditions", keywords: ["mutual exclusion", "hold and wait", "no preemption", "circular wait"] },
        { label: "Prevention or avoidance (e.g. lock ordering, Banker's algorithm)", keywords: ["prevent", "lock order", "banker", "avoid"] },
        { label: "Detection and recovery", keywords: ["detect", "recover", "kill", "rollback"] }
      ],
      strongerAnswer: "A deadlock occurs when processes each hold a resource and wait for one held by another, so none can proceed. It requires mutual exclusion, hold and wait, no preemption and circular wait at the same time. Prevention removes one condition — for example, always acquiring locks in a fixed order breaks circular wait. Avoidance (Banker's algorithm) only grants requests that keep the system in a safe state, and detection-and-recovery terminates or rolls back processes."
    }
  ],
  "Computer Networks": [
    {
      question: "Explain the differences between TCP and UDP.",
      keyPoints: [
        { label: "TCP is connection-oriented with a handshake", keywords: ["connection", "handshake", "syn"] },
        { label: "TCP guarantees reliable, ordered delivery", keywords: ["reliable", "order", "retransmi", "acknowledg"] },
        { label: "UDP is faster with lower overhead", keywords: ["fast", "overhead", "lightweight", "connectionless"] },
        { label: "Gives use cases for each", keywords: ["video", "dns", "gaming", "streaming", "web", "email", "http"] }
      ],
      strongerAnswer: "TCP is connection-oriented: it opens a connection with a three-way handshake and guarantees reliable, ordered delivery using acknowledgements, retransmission, flow control and congestion control. UDP is connectionless and sends datagrams without these guarantees, so it has lower latency and overhead. TCP suits web pages, email and file transfer; UDP suits DNS lookups, video calls and online gaming where speed matters more than every packet."
    }
  ],
  Git: [
    {
      question: "Explain the difference between git merge and git rebase.",
      keyPoints: [
        { label: "Merge preserves history and may create a merge commit", keywords: ["merge commit", "preserve", "history"] },
        { label: "Rebase replays commits for a linear history", keywords: ["linear", "replay", "rewrite"] },
        { label: "Do not rebase shared or public branches", keywords: ["shared", "public", "pushed", "force"] },
        { label: "Mentions conflict resolution", keywords: ["conflict"] }
      ],
      strongerAnswer: "git merge combines two branches and keeps both histories, adding a merge commit when they have diverged. git rebase replays your commits on top of another branch, producing a clean linear history but rewriting commit IDs. Because rebasing rewrites history, I only rebase local or personal branches and never commits that teammates have already pulled. Both may require resolving conflicts."
    }
  ],
  "REST APIs": [
    {
      question: "What makes an API RESTful?",
      keyPoints: [
        { label: "Resources identified by URIs using nouns", keywords: ["resource", "uri", "url", "noun"] },
        { label: "Uses standard HTTP methods", keywords: ["get", "post", "put", "delete", "http method"] },
        { label: "Statelessness", keywords: ["stateless"] },
        { label: "Meaningful status codes and representations (JSON)", keywords: ["status code", "json", "representation", "200", "404"] }
      ],
      strongerAnswer: "A RESTful API models data as resources with noun-based URIs such as /api/orders/42, uses standard HTTP methods for actions (GET, POST, PUT, PATCH, DELETE), and is stateless — each request carries all the context it needs, such as an auth token. It returns meaningful status codes and representations like JSON, supports caching where appropriate and keeps a consistent, versioned interface."
    }
  ],
  "Spring Security": [
    {
      question: "Explain how JWT authentication works in a Spring Boot REST API.",
      keyPoints: [
        { label: "Login verifies credentials and issues a signed token", keywords: ["login", "sign", "issue", "generate", "credentials"] },
        { label: "Client sends the token in the Authorization header", keywords: ["authorization", "bearer", "header"] },
        { label: "A filter validates signature and expiry, then sets the SecurityContext", keywords: ["filter", "validate", "signature", "expir", "securitycontext", "security context"] },
        { label: "Roles drive authorization (hasRole / @PreAuthorize)", keywords: ["role", "hasrole", "preauthorize", "authoriz"] }
      ],
      strongerAnswer: "The client posts credentials to /api/auth/login. Spring's AuthenticationManager verifies them with a UserDetailsService and BCrypt PasswordEncoder, and the server returns a signed JWT containing the user ID, roles and an expiry. On every later request the client sends Authorization: Bearer <token>. A custom OncePerRequestFilter validates the signature and expiry and puts the Authentication into the SecurityContext. Authorization rules such as hasRole('ADMIN') or @PreAuthorize then allow or reject the request. Because no session is stored, the API stays stateless; short expiries and refresh tokens limit the impact of a stolen token."
    }
  ],
  Docker: [
    {
      question: "How would you containerise a Spring Boot application with a MySQL database?",
      keyPoints: [
        { label: "Dockerfile with a JRE base image that runs the jar", keywords: ["dockerfile", "jre", "jar", "base image", "from"] },
        { label: "Docker Compose to run the app and database together", keywords: ["compose", "docker-compose", "services"] },
        { label: "Containers communicate by service name", keywords: ["service name", "network", "hostname", "db:3306"] },
        { label: "Volume for database persistence and environment variables for config", keywords: ["volume", "persist", "environment", "env"] }
      ],
      strongerAnswer: "I would build the jar, then write a Dockerfile based on a slim JRE image that copies the jar and runs it with ENTRYPOINT, ideally using a multi-stage build. A docker-compose.yml would define an api service and a db service on the same network, so the API connects with jdbc:mysql://db:3306/careerai. Database credentials and URLs come from environment variables rather than being baked into the image, and the MySQL data directory is mounted on a named volume so it survives container restarts."
    }
  ]
};
