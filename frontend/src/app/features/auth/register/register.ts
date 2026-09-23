import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { friendlyErrorMessage } from '../../../core/utils/error';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: '../login/login.css',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const { email, password, fullName } = this.form.getRawValue();

    this.authService.register({ email, password, fullName }).subscribe({
      next: () => {
        // Registration itself does not sign the customer in (see auth-service's AuthController — it only
        // returns the new profile, not tokens), so log in immediately with the same credentials for a
        // one-step signup flow instead of making them re-type on a second screen.
        this.authService.login({ email, password }).subscribe({
          next: () => this.router.navigateByUrl('/'),
          error: () => {
            this.submitting.set(false);
            this.router.navigate(['/login']);
          },
        });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(friendlyErrorMessage(error));
      },
    });
  }
}
