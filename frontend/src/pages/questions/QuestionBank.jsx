import {
  useState
} from "react";

import {
  FiSearch,
  FiBookOpen,
  FiCode,
  FiDatabase,
  FiFilter,
  FiArrowRight
} from "react-icons/fi";


function QuestionBank() {

  const [search, setSearch] =
    useState("");


  const questions = [

    {
      category: "Java",
      difficulty: "Beginner",
      question:
        "What is the difference between == and equals() in Java?"
    },

    {
      category: "Spring Boot",
      difficulty: "Intermediate",
      question:
        "Explain dependency injection in Spring Boot."
    },

    {
      category: "SQL",
      difficulty: "Intermediate",
      question:
        "What is the difference between WHERE and HAVING?"
    },

    {
      category: "React",
      difficulty: "Beginner",
      question:
        "What is the purpose of useState in React?"
    },

    {
      category: "DBMS",
      difficulty: "Intermediate",
      question:
        "Explain database normalization and its advantages."
    },

    {
      category: "Operating Systems",
      difficulty: "Advanced",
      question:
        "What is the difference between a process and a thread?"
    }

  ];


  const filtered =
    questions.filter(
      (item) =>
        item.question
          .toLowerCase()
          .includes(
            search.toLowerCase()
          ) ||
        item.category
          .toLowerCase()
          .includes(
            search.toLowerCase()
          )
    );


  return (
    <div className="tool-page">

      <div className="section-container">

        <div className="tool-page-header">

          <span className="section-tag">
            QUESTION BANK
          </span>

          <h1>
            Practice interview questions by topic
          </h1>

          <p>
            Search technical questions, filter by topic and difficulty
            and use them as part of your CareerAI preparation.
          </p>

        </div>


        <div className="question-bank-toolbar">

          <div className="question-search">

            <FiSearch />

            <input
              type="text"
              placeholder="Search Java, SQL, React..."
              value={search}
              onChange={(event) =>
                setSearch(
                  event.target.value
                )
              }
            />

          </div>


          <button>
            <FiFilter />
            Filters
          </button>

        </div>


        <div className="question-category-row">

          <button>
            <FiBookOpen />
            Technical
          </button>

          <button>
            <FiCode />
            Coding
          </button>

          <button>
            <FiDatabase />
            SQL
          </button>

        </div>


        <div className="question-bank-list">

          {filtered.map(
            (item, index) => (

              <div
                className="question-bank-item"
                key={
                  item.question
                }
              >

                <div className="question-number">
                  {String(index + 1)
                    .padStart(2, "0")}
                </div>


                <div className="question-bank-content">

                  <div>

                    <span>
                      {item.category}
                    </span>

                    <span>
                      {item.difficulty}
                    </span>

                  </div>


                  <h3>
                    {item.question}
                  </h3>

                </div>


                <button>
                  Practice
                  <FiArrowRight />
                </button>

              </div>

            )
          )}

        </div>

      </div>

    </div>
  );
}


export default QuestionBank;