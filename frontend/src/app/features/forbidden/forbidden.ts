import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="card empty-state">
      <h2>Not available for your role</h2>
      <p class="muted">This page isn't part of your account type.</p>
      <a class="btn" routerLink="/">Go home</a>
    </div>
  `,
})
export class Forbidden {}
