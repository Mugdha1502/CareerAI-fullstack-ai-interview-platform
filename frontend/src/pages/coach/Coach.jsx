import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";

import {
  FiPlus,
  FiSend,
  FiTrash2,
  FiMessageCircle,
  FiInfo,
  FiZap,
  FiArrowRight,
  FiClock,
  FiList,
  FiAlertCircle
} from "react-icons/fi";

import { useAuth } from "../../context/AuthContext";
import { toApiError } from "../../services/api";
import {
  createConversation,
  deleteConversation as deleteConversationApi,
  getConversation,
  getConversations,
  sendCoachMessage
} from "../../services/coachService";
import { SUGGESTED_PROMPTS } from "./coachService";

const FALLBACK_LABEL =
  "AI Coach is temporarily unavailable. Here is a recommendation based on your CareerAI progress.";

function Coach() {
  const { isAuthenticated } = useAuth();

  const [conversations, setConversations] = useState([]);
  const [listStatus, setListStatus] = useState(isAuthenticated ? "loading" : "guest");
  const [activeId, setActiveId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [input, setInput] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [historyOpen, setHistoryOpen] = useState(false);

  const messagesRef = useRef(null);
  const inputRef = useRef(null);
  // Set while send() creates a conversation, so the history loader does not wipe the new messages.
  const skipLoadRef = useRef(null);
  const tempCounter = useRef(0);

  // Load the conversation list once.
  useEffect(() => {
    if (!isAuthenticated) return undefined;
    let cancelled = false;

    getConversations()
      .then((list) => {
        if (cancelled) return;
        setConversations(list);
        setActiveId(list[0]?.id || null);
        setListStatus("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(toApiError(err).message);
        setListStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  // Load messages whenever another conversation is opened.
  useEffect(() => {
    if (!activeId || skipLoadRef.current === activeId) return undefined;
    let cancelled = false;

    setMessagesLoading(true);
    getConversation(activeId)
      .then((data) => {
        if (!cancelled) setMessages(data.messages);
      })
      .catch((err) => {
        if (!cancelled) setError(toApiError(err).message);
      })
      .finally(() => {
        if (!cancelled) setMessagesLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [activeId]);

  // Keep the newest message in view.
  useEffect(() => {
    const element = messagesRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [messages.length, pending]);

  const upsertConversation = (conversation) => {
    setConversations((current) => [conversation, ...current.filter((item) => item.id !== conversation.id)]);
  };

  const send = async (rawText) => {
    const text = rawText.trim();
    if (!text || pending || !isAuthenticated) return;

    setError("");
    setInput("");
    setPending(true);

    let conversationId = activeId;
    tempCounter.current += 1;
    const tempId = `pending-${tempCounter.current}`;

    try {
      if (!conversationId) {
        const created = await createConversation();
        conversationId = created.id;
        skipLoadRef.current = created.id;
        upsertConversation(created);
        setActiveId(created.id);
        setMessages([]);
      }

      setMessages((current) => [...current, { id: tempId, role: "USER", content: text }]);

      const response = await sendCoachMessage(conversationId, text);

      setMessages((current) => [
        ...current.filter((message) => message.id !== tempId),
        response.userMessage,
        response.reply
      ]);
      upsertConversation(response.conversation);
    } catch (err) {
      setMessages((current) => current.filter((message) => message.id !== tempId));
      setInput(text);
      setError(`The Coach could not answer: ${toApiError(err).message}`);
    } finally {
      setPending(false);
      inputRef.current?.focus();
    }
  };

  const newConversation = () => {
    setActiveId(null);
    setMessages([]);
    setInput("");
    setError("");
    setHistoryOpen(false);
    inputRef.current?.focus();
  };

  const openConversation = (id) => {
    if (id === activeId) {
      setHistoryOpen(false);
      return;
    }
    skipLoadRef.current = null;
    setMessages([]);
    setError("");
    setActiveId(id);
    setHistoryOpen(false);
  };

  const removeConversation = async (conversation) => {
    if (!window.confirm(`Delete "${conversation.title}"?`)) return;

    try {
      await deleteConversationApi(conversation.id);
      setConversations((current) => current.filter((item) => item.id !== conversation.id));
      if (activeId === conversation.id) {
        setActiveId(null);
        setMessages([]);
      }
    } catch (err) {
      setError(toApiError(err).message);
    }
  };

  const handleKeyDown = (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      send(input);
    }
  };

  const disabled = !isAuthenticated || listStatus !== "ready";

  return (
    <div className="ua-page aicoach-page">

      <header className="ua-page-header">
        <div>
          <span className="ua-eyebrow">AI COACH</span>
          <h1>Career coach</h1>
          <p>Ask what to practise, how to raise your readiness or how to prepare for interviews.</p>
        </div>
      </header>

      <div className="ua-demo-banner">
        <FiInfo aria-hidden="true" />
        <p>
          {isAuthenticated ? (
            <>
              <strong>Personalised from your CareerAI progress.</strong> The Coach uses your skill gap, plan
              and practice results. AI can make mistakes — check important advice.
            </>
          ) : (
            <>
              <strong>Sign in to use the AI Coach.</strong> Conversations are saved to your CareerAI account.{" "}
              <Link to="/login">Log in</Link>
            </>
          )}
        </p>
      </div>


      <div className="aicoach-shell">

        {/* HISTORY */}

        <aside className={`aicoach-history${historyOpen ? " open" : ""}`}>

          <button
            type="button"
            className="ua-button primary full"
            onClick={newConversation}
            disabled={disabled}
          >
            <FiPlus aria-hidden="true" />
            New conversation
          </button>

          <h2 className="aicoach-history-title">History</h2>

          {listStatus === "loading" && <p className="ua-muted aicoach-history-empty">Loading conversations…</p>}

          {listStatus === "ready" && conversations.length > 0 && (
            <ul className="aicoach-history-list">
              {conversations.map((conversation) => (
                <li
                  key={conversation.id}
                  className={conversation.id === activeId ? "active" : ""}
                >
                  <button
                    type="button"
                    className="aicoach-history-item"
                    onClick={() => openConversation(conversation.id)}
                    aria-current={conversation.id === activeId ? "true" : undefined}
                  >
                    <FiMessageCircle aria-hidden="true" />
                    <span>
                      <strong>{conversation.title}</strong>
                      <small>
                        <FiClock aria-hidden="true" />
                        {new Date(conversation.updatedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })}
                        {" · "}
                        {conversation.messageCount} messages
                      </small>
                    </span>
                  </button>
                  <button
                    type="button"
                    className="ua-icon-button danger small"
                    onClick={() => removeConversation(conversation)}
                    aria-label={`Delete conversation ${conversation.title}`}
                  >
                    <FiTrash2 aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          )}

          {listStatus === "ready" && conversations.length === 0 && (
            <p className="ua-muted aicoach-history-empty">Your conversations will appear here.</p>
          )}

        </aside>


        {/* CHAT */}

        <section
          className="aicoach-chat"
          aria-label="Coach conversation"
        >

          <div className="aicoach-chat-header">
            <span className="aicoach-avatar" aria-hidden="true"><FiZap /></span>
            <div>
              <strong>CareerAI Coach</strong>
              <small>{isAuthenticated ? "Personalised career coach" : "Sign in required"}</small>
            </div>
            <button
              type="button"
              className="ua-button small ghost aicoach-history-toggle"
              onClick={() => setHistoryOpen((value) => !value)}
              aria-expanded={historyOpen}
            >
              <FiList aria-hidden="true" />
              History
            </button>
          </div>

          <div
            className="aicoach-messages"
            ref={messagesRef}
            aria-live="polite"
          >

            {messagesLoading && <p className="ua-muted aicoach-history-empty">Loading messages…</p>}

            {!messagesLoading && messages.length === 0 && !pending && (
              <div className="aicoach-welcome">
                <span className="aicoach-avatar large" aria-hidden="true"><FiZap /></span>
                <h2>How can I help you prepare today?</h2>
                <p>Pick a suggestion or type your own question.</p>
              </div>
            )}

            {messages.map((message) => {
              const isUser = message.role === "USER";

              return (
                <div
                  key={message.id}
                  className={`aicoach-message ${isUser ? "user" : "coach"}`}
                >
                  <span className="sr-only">{isUser ? "You said:" : "Coach replied:"}</span>
                  <div className="aicoach-bubble">
                    {message.source === "FALLBACK" && (
                      <p className="aicoach-fallback-note">
                        <FiAlertCircle aria-hidden="true" />
                        {FALLBACK_LABEL}
                      </p>
                    )}
                    <p className="aicoach-text">{message.content}</p>
                    {message.suggestedActions?.length > 0 && (
                      <div className="aicoach-links">
                        {message.suggestedActions.map((action) => (
                          <Link key={`${action.label}-${action.route}`} to={action.route}>
                            {action.label}
                            <FiArrowRight aria-hidden="true" />
                          </Link>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}

            {pending && (
              <div className="aicoach-message coach">
                <div className="aicoach-bubble aicoach-typing">
                  <span className="sr-only">Coach is typing</span>
                  <i aria-hidden="true"></i>
                  <i aria-hidden="true"></i>
                  <i aria-hidden="true"></i>
                </div>
              </div>
            )}

          </div>

          {error && (
            <p className="aicoach-error" role="alert">
              <FiAlertCircle aria-hidden="true" />
              {error}
            </p>
          )}


          <div className="aicoach-prompts" role="group" aria-label="Suggested prompts">
            {SUGGESTED_PROMPTS.map((prompt) => (
              <button
                key={prompt}
                type="button"
                onClick={() => send(prompt)}
                disabled={pending || disabled}
              >
                {prompt}
              </button>
            ))}
          </div>


          <form
            className="aicoach-composer"
            onSubmit={(event) => {
              event.preventDefault();
              send(input);
            }}
          >
            <label htmlFor="aicoach-input" className="sr-only">Message the coach</label>
            <textarea
              id="aicoach-input"
              ref={inputRef}
              rows={1}
              maxLength={2000}
              placeholder={isAuthenticated ? "Ask about practice, readiness, interviews or your resume…" : "Sign in to chat with the Coach"}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={handleKeyDown}
              disabled={disabled}
            />
            <button
              type="submit"
              className="aicoach-send"
              disabled={!input.trim() || pending || disabled}
              aria-label="Send message"
            >
              <FiSend aria-hidden="true" />
            </button>
          </form>

        </section>

      </div>

    </div>
  );
}

export default Coach;
