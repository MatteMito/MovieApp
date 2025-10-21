import { AnalyticsService } from './analytics.service';
export declare class AnalyticsController {
    private readonly analyticsService;
    private readonly logger;
    constructor(analyticsService: AnalyticsService);
    getBasicStats(): Promise<{
        success: boolean;
        data: import("./analytics.service").BasicStats;
        message: string;
        timestamp: string;
    }>;
    getGenreStats(limit?: string): Promise<{
        success: boolean;
        data: import("./analytics.service").GenreStats[];
        message: string;
        timestamp: string;
    }>;
    getYearStats(): Promise<{
        success: boolean;
        data: import("./analytics.service").YearStats[];
        message: string;
        timestamp: string;
    }>;
    getDirectorStats(limit?: string): Promise<{
        success: boolean;
        data: import("./analytics.service").DirectorStats[];
        message: string;
        timestamp: string;
    }>;
    getRatingDistribution(): Promise<{
        success: boolean;
        data: {
            [key: string]: number;
        };
        message: string;
        timestamp: string;
    }>;
    getDecadeDistribution(): Promise<{
        success: boolean;
        data: {
            [key: string]: number;
        };
        message: string;
        timestamp: string;
    }>;
    getAdvancedAnalytics(): Promise<{
        success: boolean;
        data: import("./analytics.service").AdvancedAnalytics;
        message: string;
        timestamp: string;
    }>;
    getTextReport(): Promise<{
        success: boolean;
        data: {
            report: string;
        };
        message: string;
        timestamp: string;
    }>;
}
