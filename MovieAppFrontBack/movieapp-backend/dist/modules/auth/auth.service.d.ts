import { JwtService } from '@nestjs/jwt';
import { Repository } from 'typeorm';
import { UserEntity } from '../../database/entities/user.entity';
export interface RegisterDto {
    email: string;
    password: string;
    username?: string;
}
export interface LoginDto {
    email: string;
    password: string;
}
export interface AuthResponse {
    access_token: string;
    user: {
        id: string;
        email: string;
        username?: string;
    };
}
export declare class AuthService {
    private userRepository;
    private jwtService;
    private readonly logger;
    constructor(userRepository: Repository<UserEntity>, jwtService: JwtService);
    register(dto: RegisterDto): Promise<AuthResponse>;
    login(dto: LoginDto): Promise<AuthResponse>;
    validateToken(token: string): Promise<UserEntity | null>;
    getUserById(userId: string): Promise<UserEntity | null>;
    private generateToken;
}
