/** Оператор платформи (окрема сесія від співробітника підприємства). */
export interface PlatformOperator {
  id: string;
  email: string;
}

export interface PlatformLoginRequest {
  email: string;
  password: string;
}

/** Відповідь login / refresh оператора платформи. */
export interface PlatformAuthTokensResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  operator: PlatformOperator;
}

export interface PlatformAuthState {
  accessToken: string | null;
  refreshToken: string | null;
  operator: PlatformOperator | null;
}

/** Підприємство зі списку оператора. */
export interface Enterprise {
  id: string;
  code: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEnterpriseRequest {
  name: string;
  code: string;
  adminEmail: string;
  adminPassword: string;
}
