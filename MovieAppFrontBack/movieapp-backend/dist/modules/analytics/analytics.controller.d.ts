import { AnalyticsService } from './analytics.service';
interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    message?: string;
    timestamp: string;
}
export declare class AnalyticsController {
    private readonly analyticsService;
    private readonly logger;
    constructor(analyticsService: AnalyticsService);
    getBasicStats(userId: string): Promise<ApiResponse>;
    getGenreStats(userId: string, limit?: string): Promise<ApiResponse>;
    getYearStats(userId: string): Promise<ApiResponse>;
    getDirectorStats(userId: string, limit?: string): Promise<ApiResponse>;
    getAdvancedAnalytics(userId: string): Promise<ApiResponse>;
}
export {};
