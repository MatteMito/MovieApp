import { AuthService, RegisterDto, LoginDto } from './auth.service';
export declare class AuthController {
    private readonly authService;
    private readonly logger;
    constructor(authService: AuthService);
    register(dto: RegisterDto): Promise<{
        success: boolean;
        data: import("./auth.service").AuthResponse;
        message: string;
        timestamp: string;
    }>;
    login(dto: LoginDto): Promise<{
        success: boolean;
        data: import("./auth.service").AuthResponse;
        message: string;
        timestamp: string;
    }>;
}
