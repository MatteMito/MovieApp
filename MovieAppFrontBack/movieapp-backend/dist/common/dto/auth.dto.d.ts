export declare class RegisterDto {
    email: string;
    password: string;
    username?: string;
}
export declare class LoginDto {
    email: string;
    password: string;
}
export declare class AuthResponse {
    access_token: string;
    user: {
        id: string;
        email: string;
        username?: string;
        created_at: Date;
    };
}
