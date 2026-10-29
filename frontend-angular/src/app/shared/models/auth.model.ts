export type UserRole = 'admin' | 'manager' | 'driver' | 'user';

export type PersonType = 'INDIVIDUAL' | 'LEGAL_ENTITY_REPRESENTATIVE';

export type ContactChannel = 'EMAIL' | 'PHONE' | 'MESSENGERS';

export interface AuthUserContactPhone {
  id: string;
  phone: string;
  primary: boolean;
  telegram: boolean;
  whatsapp: boolean;
  viber: boolean;
}

export interface AuthUserProfile {
  lastName: string | null;
  firstName: string | null;
  patronymic: string | null;
  personType: PersonType | null;
  legalEntityEdrpou: string | null;
  preferredChannels: ContactChannel[];
  phones: AuthUserContactPhone[];
  profileComplete: boolean;
}

export interface AuthUser {
  id: string;
  email: string;
  /** Код підприємства співробітника (відображається в оболонці). */
  enterpriseCode?: string;
  role: UserRole;
  /** Призначені ролі (якщо немає — лише active). */
  availableRoles?: UserRole[];
  displayName?: string;
  profile?: AuthUserProfile;
}

export interface LoginRequest {
  enterpriseCode: string;
  email: string;
  password: string;
}

export interface RegisterRequest {
  enterpriseCode: string;
  email: string;
  password: string;
}

export interface RegisterResponse {
  id: string;
  email: string;
  role: UserRole;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ForgotPasswordRequest {
  enterpriseCode: string;
  email: string;
}

export interface ResendVerificationRequest {
  enterpriseCode: string;
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface PasswordResetInfoRequest {
  token: string;
}

export interface PasswordResetInfoResponse {
  email: string;
}

export interface OperationSuccessResponse {
  success: boolean;
  message: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export type RefreshResponse = LoginResponse;

export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
}
