export interface AskRequest {
  question: string;
}

export interface AskResponse {
  answer: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}
