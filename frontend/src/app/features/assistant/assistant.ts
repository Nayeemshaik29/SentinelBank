import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AiService } from '../../core/services/ai.service';
import { ChatMessage } from '../../core/models/ai.model';
import { friendlyErrorMessage } from '../../core/utils/error';

@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './assistant.html',
  styleUrl: './assistant.css',
})
export class Assistant {
  private readonly aiService = inject(AiService);

  readonly messages = signal<ChatMessage[]>([
    {
      role: 'assistant',
      text: "Hi! I can answer questions about your accounts and recent transfers — for example, \"What's my balance?\" or \"What was my last transfer?\". I can only give you information; I can't make transfers or change anything.",
    },
  ]);
  readonly draft = signal('');
  readonly sending = signal(false);
  readonly errorMessage = signal<string | null>(null);

  @ViewChild('scrollAnchor') private scrollAnchor?: ElementRef<HTMLDivElement>;

  send(): void {
    const question = this.draft().trim();
    if (!question || this.sending()) {
      return;
    }
    this.errorMessage.set(null);
    this.messages.update((current) => [...current, { role: 'user', text: question }]);
    this.draft.set('');
    this.sending.set(true);
    this.scrollToBottom();

    this.aiService.ask({ question }).subscribe({
      next: (response) => {
        this.messages.update((current) => [...current, { role: 'assistant', text: response.answer }]);
        this.sending.set(false);
        this.scrollToBottom();
      },
      error: (error: unknown) => {
        this.errorMessage.set(friendlyErrorMessage(error));
        this.sending.set(false);
      },
    });
  }

  private scrollToBottom(): void {
    // Runs after Angular has rendered the new message, not before — a plain setTimeout(0) is enough to
    // land after the current render pass without pulling in a whole afterNextRender dependency chain.
    setTimeout(() => this.scrollAnchor?.nativeElement.scrollIntoView({ behavior: 'smooth' }), 0);
  }
}
