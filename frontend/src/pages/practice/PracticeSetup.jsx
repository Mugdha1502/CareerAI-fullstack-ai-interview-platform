import { useState } from "react";
import { FiArrowRight, FiCheck } from "react-icons/fi";

const normalizeOption = (option) =>
  typeof option === "string" ? { value: option, label: option } : option;

/**
 * Generic setup panel shared by every practice module.
 *
 * fields: [{
 *   key, label, options, required, defaultValue,
 *   layout: "pills" | "cards" | "segmented",
 *   showWhen(selections), missingMessage
 * }]
 * The field with key "topic" is stored in selectedTopic (optionally preselected via initialTopic).
 */
function PracticeSetup({ fields, startLabel = "Start Practice", initialTopic = "", initialValues = {}, onStart }) {
  const [selectedTopic, setSelectedTopic] = useState(initialTopic);
  const [selections, setSelections] = useState(() =>
    Object.fromEntries(
      fields
        .filter((field) => field.key !== "topic")
        .map((field) => [field.key, initialValues[field.key] ?? field.defaultValue ?? ""])
    )
  );

  const values = { ...selections, topic: selectedTopic };

  const visibleFields = fields.filter((field) => !field.showWhen || field.showWhen(values));

  const firstMissing = visibleFields.find((field) => field.required && !values[field.key]);

  const choose = (key, value) => {
    if (key === "topic") {
      setSelectedTopic(value);
      return;
    }

    setSelections((current) => ({ ...current, [key]: value }));
  };

  const handleStart = () => {
    if (!firstMissing) {
      onStart(values);
    }
  };

  return (
    <div className="practice-setup-card practice-setup-panel">

      <div className="practice-detail-section-title">
        <div>
          <span>SETUP</span>
          <h2>Choose your practice</h2>
        </div>
      </div>


      {visibleFields.map((field, index) => (
        <fieldset
          key={field.key}
          className="practice-setup-group"
        >

          <legend>
            <span>{String(index + 1).padStart(2, "0")}</span>
            {field.label}
            {field.required && !values[field.key] && (
              <em>Required</em>
            )}
          </legend>

          <div className={`practice-option-grid ${field.layout || "pills"}`}>

            {field.options.map(normalizeOption).map((option) => {
              const isSelected = values[field.key] === option.value;

              return (
                <button
                  type="button"
                  key={option.value}
                  className={`practice-option${isSelected ? " selected" : ""}`}
                  aria-pressed={isSelected}
                  onClick={() => choose(field.key, option.value)}
                >

                  {option.icon && (
                    <span className="practice-option-icon">
                      {option.icon}
                    </span>
                  )}

                  <span className="practice-option-text">
                    <strong>{option.label}</strong>
                    {option.description && <small>{option.description}</small>}
                  </span>

                  {isSelected && (
                    <FiCheck className="practice-option-check" />
                  )}

                </button>
              );
            })}

          </div>

        </fieldset>
      ))}


      <div className="practice-setup-footer">

        <p className={`practice-setup-status${firstMissing ? "" : " ready"}`}>
          {firstMissing
            ? firstMissing.missingMessage || `Select ${firstMissing.label.toLowerCase()} to continue`
            : "Ready to start"}
        </p>

        <button
          type="button"
          className="primary-button practice-start-cta"
          onClick={handleStart}
          disabled={Boolean(firstMissing)}
        >
          {startLabel}
          <FiArrowRight />
        </button>

      </div>

    </div>
  );
}

export default PracticeSetup;
